package aspectj

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

/**
 * Gradle plugin to easily apply AspectJ functionality to Gradle projects.
 *
 * @author Luke Taylor
 * @author Mike Noordermeer
 */
class AspectJPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.plugins.apply(JavaPlugin)

        if (!project.hasProperty('aspectjVersion')) {
            throw new GradleException("You must set the property 'aspectjVersion' before applying the aspectj plugin")
        }

        if (project.configurations.findByName('ajtools') == null) {
            project.configurations.create('ajtools')
            project.dependencies {
                ajtools "org.aspectj:aspectjtools:${project.aspectjVersion}"
                compile "org.aspectj:aspectjrt:${project.aspectjVersion}"
            }
        }

        for(configuration in ['aspectpath', 'ajInpath', 'testAspectpath', 'testAjInpath']) {
            if (project.configurations.findByName(configuration) == null) {
                project.configurations.create(configuration)
            }
        }

        if (!project.sourceSets.main.allJava.isEmpty()) {
            project.tasks.create(name: 'compileAspect', overwrite: true, description: 'Compiles AspectJ Source', type: Ajc) {
                dependsOn project.configurations*.getTaskDependencyFromProjectDependency(true, "assemble")

                dependsOn project.processResources
                sourceSet = project.sourceSets.main
                inputs.files(sourceSet.allJava)
                outputs.dir(sourceSet.output.classesDir)
                aspectPath = project.configurations.aspectpath
                ajInpath = project.configurations.ajInpath
            }

            project.tasks.compileJava.deleteAllActions()
            project.tasks.compileJava.dependsOn project.tasks.compileAspect
        }

        if (!project.sourceSets.test.allJava.isEmpty()) {
            project.tasks.create(name: 'compileTestAspect', overwrite: true, description: 'Compiles AspectJ Test Source', type: Ajc) {
                dependsOn project.processTestResources, project.compileJava
                sourceSet = project.sourceSets.test
                inputs.files(sourceSet.allJava)
                outputs.dir(sourceSet.output.classesDir)
                aspectPath = project.configurations.testAspectpath
                ajInpath = project.configurations.testAjInpath
            }

            project.tasks.compileTestJava.deleteAllActions()
            project.tasks.compileTestJava.dependsOn project.tasks.compileTestAspect
        }
    }
}

class Ajc extends DefaultTask {

    SourceSet sourceSet

    FileCollection aspectPath
    FileCollection ajInpath

    // ignore or warning
    String xlint = 'ignore'

    String maxmem

    Ajc() {
        logging.captureStandardOutput(LogLevel.INFO)
    }

    @TaskAction
    def compile() {
        logger.info("="*30)
        logger.info("="*30)
        logger.info("Running ajc ...")
        logger.info("classpath: ${sourceSet.compileClasspath.asPath}")
        logger.info("srcDirs $sourceSet.java.srcDirs")

        def iajcArgs = [classpath: sourceSet.compileClasspath.asPath,
            destDir: sourceSet.output.classesDir.absolutePath,
            source: project.convention.plugins.java.sourceCompatibility,
            target: project.convention.plugins.java.targetCompatibility,
            inpath: ajInpath.asPath,
            xlint: xlint,
            fork: 'true',
            aspectPath: aspectPath.asPath,
            sourceRootCopyFilter: '**/*.java,**/*.aj',
            showWeaveInfo: 'true']

        if (maxmem != null) {
            iajcArgs['maxmem'] = maxmem
        }

        ant.taskdef(resource: "org/aspectj/tools/ant/taskdefs/aspectjTaskdefs.properties", classpath: project.configurations.ajtools.asPath)
        ant.iajc(iajcArgs) {
            sourceroots {
                sourceSet.java.srcDirs.each {
                    logger.info("   sourceRoot $it")
                    pathelement(location: it.absolutePath)
                }
            }
        }
    }
}
