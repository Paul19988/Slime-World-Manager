pipeline {
    agent any
    options {
        gitLabConnection('Gitlab')
    }
    triggers {
        gitlab(triggerOnPush: true, triggerOnMergeRequest: true, branchFilterType: 'All')
    }
     post {
          failure {
            updateGitlabCommitStatus name: 'build', state: 'failed'
          }
          success {
            updateGitlabCommitStatus name: 'build', state: 'success'
          }
        }
    tools {
        maven 'maven'
        jdk 'java'
    }
    stages {
        stage ('Initialize') {
            steps {
                sh '''
                    wget https://cdn.getbukkit.org/spigot/spigot-1.16.1.jar && mvn install:install-file -Dfile=spigot-1.16.1.jar -DgroupId=org.spigotmc -DartifactId=spigot -Dversion=1.16.1-R0.1-SNAPSHOT -Dpackaging=jar
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
                '''
            }
        }

        stage ('Build') {
            steps {
                dir('network'){
                    sh 'mvn -Dmaven.test.failure.ignore=true install'
                }
            }
        }

        stage('Create plugins.zip') {
            steps {
                dir('network'){
                    echo 'Creating plugins.zip file...';
                    sh 'mkdir -p temp';
                    sh 'cp -r target/* temp/';
                    zip archive: true, dir: 'temp', zipFile: 'plugins.zip';
                    sh 'rm -r temp/';
                }
            }
        }

        stage('Release') {
              steps {
                    dir('network'){
                        echo 'Creating release file...';
                        sh "mkdir -p output"
                        sh "mv target/* output/"
                        archiveArtifacts artifacts: 'output/*'
                    }
              }
        }

        stage('CleanWorkspace') {
            steps {
                cleanWs()
            }
        }
    }
}

