/*
 * Copyright 2015-2018 Thorsten Ehlers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.idlestate.gradle.downloaddependencies

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

/**
 * Gradle-Plugin that creates a local maven repository with all dependencies. 
 */
class DownloadDependenciesPlugin implements Plugin<Project> {
    static final GradleVersion MINIMAL_GRADLE_VERSION = GradleVersion.version( '2.3' )
    static final String DOWNLOAD_DEPENDENCIES_TASK = 'downloadDependencies'
    static final String CLEANUP_LOCAL_REPOSITORY_TASK = 'cleanupLocalRepository'

    void apply( Project project ) {
        if ( GradleVersion.current() < MINIMAL_GRADLE_VERSION ) {
            throw new GradleException( "${this.class.simpleName} only works with Gradle >= ${MINIMAL_GRADLE_VERSION}" )
        }

        project.task( DOWNLOAD_DEPENDENCIES_TASK, type: DownloadDependenciesTask, group: 'Build Setup', description: 'Downloads all dependencies into a local directory based repository.' )

        project.task( CLEANUP_LOCAL_REPOSITORY_TASK, type: DownloadDependenciesTask, group: 'Build Setup', description: 'Remove unused dependencies from local repository' ) {
            doLast {
                ext.actualRepository = getLocalRepository( project )

                logger.info( "Moving cleaned up repository from ${localRepository.absolutePath} to ${actualRepository.absolutePath}." )
                project.delete( actualRepository )
                project.copy {
                    from localRepository
                    into actualRepository
                }
                project.delete( localRepository )
            }
        }

        project.afterEvaluate {
            project.tasks[ DOWNLOAD_DEPENDENCIES_TASK ].localRepository = getLocalRepository( project )
            project.tasks[ CLEANUP_LOCAL_REPOSITORY_TASK ].localRepository = project.file( DownloadDependenciesUtils.getTemporaryDirectory() )
        }

        final def rootRepositories = project.allprojects.getAt(0).repositories.collect()

        project.allprojects {
            // Create backup of defined repositories
//            final def definedRepositories = it.repositories.collect()

            File repository = getLocalRepository( project )

            it.logger.info( "Replacing all defined repositories in ${it.name} with local repository at ${repository}" )

            it.repositories.clear()
            it.repositories {
                maven {
                    url repository
                }
            }

            /*
            it.buildscript.repositories.clear()
            it.buildscript.repositories {
                maven {
                    url repository
                }
            }
            */

            // Use defined repositories, if download is intended
            it.gradle.taskGraph.whenReady { taskGraph ->
                if ( taskGraph.hasTask( ":${DOWNLOAD_DEPENDENCIES_TASK}" ) ) {
                    it.logger.info( "Replacing local repository in ${it.name} with defined repositories" )

                    it.repositories.addAll( rootRepositories )
                }
            }
        }

        project.configure( project ) {
            extensions.create( 'downloadDependencies', DownloadDependenciesExtension )
        }
    }

    File getLocalRepository( project ) {
        return project.downloadDependencies.localRepository
               ? project.downloadDependencies.localRepository
               : project.file( [ project.rootProject.projectDir, 'gradle', 'repository' ].join( File.separator ) )
    }
}
