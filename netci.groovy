// Groovy Script: http://www.groovy-lang.org/syntax.html
// Jenkins DSL: https://github.com/jenkinsci/job-dsl-plugin/wiki

import jobs.generation.*;

// The input project name (e.g. dotnet/corefx)
def projectName = GithubProject
// The input branch name (e.g. master)
def branchName = GithubBranchName
// Folder that the project jobs reside in (project/branch)
def projectFoldername = Utilities.getFolderName(projectName) + '/' + Utilities.getFolderName(branchName)

def windowsUnitTestMachine = 'Windows.10.Amd64.ClientRS3.DevEx.Open'

static void addRoslynJob(def myJob, String jobName, String branchName, Boolean isPr, String triggerPhraseExtra, Boolean triggerPhraseOnly = false) {
  def archiveSettings = new ArchivalSettings()
  archiveSettings.addFiles('Binaries/**/*.pdb')
  archiveSettings.addFiles('Binaries/**/*.xml')
  archiveSettings.addFiles('Binaries/**/*.log')
  archiveSettings.addFiles('Binaries/**/*.dmp')
  archiveSettings.addFiles('Binaries/**/*.zip')
  archiveSettings.addFiles('Binaries/**/*.png')
  archiveSettings.addFiles('Binaries/**/*.buildlog')
  archiveSettings.addFiles('Binaries/**/*.binlog')
  archiveSettings.excludeFiles('Binaries/Obj/**')
  archiveSettings.excludeFiles('Binaries/Bootstrap/**')
  archiveSettings.excludeFiles('Binaries/**/nuget*.zip')
  // Only archive if failed/aborted
  archiveSettings.setArchiveOnFailure()
  archiveSettings.setFailIfNothingArchived()
  Utilities.addArchival(myJob, archiveSettings)

  // Create the standard job.  This will setup parameter, SCM, timeout, etc ...
  def projectName = 'dotnet/roslyn'

  // Need to setup the triggers for the job
  if (isPr) {
    // Note the use of ' vs " for the 4th argument. We don't want groovy to interpolate this string (the ${ghprbPullId}
    // is resolved when the job is run based on an environment variable set by the Jenkins Pull Request Builder plugin.
    Utilities.standardJobSetupPR(myJob, projectName, null, '+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*');
    def triggerCore = "open|all|${jobName}"
    if (triggerPhraseExtra) {
      triggerCore = "${triggerCore}|${triggerPhraseExtra}"
    }
    def triggerPhrase = "(?im)^\\s*(@?dotnet-bot\\,?\\s+)?(re)?test\\s+(${triggerCore})(\\s+please\\.?)?\\s*\$";
    def contextName = jobName
    Utilities.addGithubPRTriggerForBranch(myJob, branchName, contextName, triggerPhrase, triggerPhraseOnly)
  } else {
    Utilities.standardJobSetupPush(myJob, projectName, "*/${branchName}");
    Utilities.addGithubPushTrigger(myJob)
    // TODO: Add once external email sending is available again
    // addEmailPublisher(myJob)
  }
}

// True when this is a PR job, false for commit.  On feature branches we do PR jobs only.
def commitPullList = [false, true]
if (branchName.startsWith("features/")) {
  commitPullList = [true]
}

// Windows Desktop CLR
commitPullList.each { isPr ->
  ['debug', 'release'].each { configuration ->
        ['unit32', 'unit64'].each { buildTarget ->
        def jobName = Utilities.getFullJobName(projectName, "windows_${configuration}_${buildTarget}", isPr)
        def myJob = job(jobName) {
            description("Windows ${configuration} tests on ${buildTarget}")
                  steps {
                    batchFile(""".\\build\\scripts\\cibuild.cmd ${(configuration == 'debug') ? '-debug' : '-release'} ${(buildTarget == 'unit32') ? '-test32' : '-test64'} -procdump -testDesktop""")
                  }
        }

        def triggerPhraseOnly = false
        def triggerPhraseExtra = ""
        Utilities.setMachineAffinity(myJob, windowsUnitTestMachine)
        Utilities.addXUnitDotNETResults(myJob, '**/xUnitResults/*.xml')
        addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
    }
  }
}

// Windows Spanish image
commitPullList.each { isPr ->
  def jobName = Utilities.getFullJobName(projectName, "windows_debug_spanish_unit32", isPr)
  def myJob = job(jobName) {
    description("Windows debug unit tests on unit32 using Spanish language")
          steps {
            batchFile(""".\\build\\scripts\\cibuild.cmd -debug -test32 -testDesktop""")
          }
  }

  def triggerPhraseOnly = false
  def triggerPhraseExtra = ""
  Utilities.setMachineAffinity(myJob, 'Windows.10.Amd64.ClientRS4.ES.Open')
  Utilities.addXUnitDotNETResults(myJob, '**/xUnitResults/*.xml')
  addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
}

// Windows CoreCLR
commitPullList.each { isPr ->
  ['debug', 'release'].each { configuration ->
    def jobName = Utilities.getFullJobName(projectName, "windows_coreclr_${configuration}", isPr)
    def myJob = job(jobName) {
      description("Windows CoreCLR unit tests")
            steps {
              batchFile(""".\\build\\scripts\\cibuild.cmd ${(configuration == 'debug') ? '-debug' : '-release'} -buildCoreClr -testCoreClr""")
            }
    }

    def triggerPhraseOnly = false
    def triggerPhraseExtra = ""
    Utilities.setMachineAffinity(myJob, windowsUnitTestMachine)
    Utilities.addXUnitDotNETResults(myJob, '**/xUnitResults/*.xml')
    addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
  }
}

// Ubuntu 16.04
commitPullList.each { isPr ->
  def jobName = Utilities.getFullJobName(projectName, "ubuntu_16_debug", isPr)
  def myJob = job(jobName) {
    description("Ubuntu 16.04 tests")
                  steps {
                    shell("./build/scripts/cibuild.sh --debug")
                  }
                }

  def triggerPhraseOnly = false
  def triggerPhraseExtra = "linux"
  Utilities.setMachineAffinity(myJob, 'Ubuntu16.04', 'latest-or-auto')
  Utilities.addXUnitDotNETResults(myJob, '**/xUnitResults/*.xml')
  addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
}

// Ubuntu 16.04 mono
commitPullList.each { isPr ->
  def jobName = Utilities.getFullJobName(projectName, "ubuntu_16_mono_debug", isPr)
  def myJob = job(jobName) {
    description("Ubuntu 16.04 mono tests")
                  steps {
                    shell("./build/scripts/cibuild.sh --debug --docker --mono")
                  }
                }

  def triggerPhraseOnly = false
  def triggerPhraseExtra = "linux"
  Utilities.setMachineAffinity(myJob, 'Ubuntu16.04', 'latest-or-auto')
  Utilities.addXUnitDotNETResults(myJob, '**/xUnitResults/*.xml')
  addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
}

// Mac
commitPullList.each { isPr ->
  def jobName = Utilities.getFullJobName(projectName, "mac_debug", isPr)
  def myJob = job(jobName) {
    description("Mac tests")
    steps {
      shell("./build/scripts/cibuild.sh --debug")
    }
  }

  def triggerPhraseOnly = true
  def triggerPhraseExtra = "mac"
  Utilities.setMachineAffinity(myJob, 'OSX10.12', 'latest-or-auto')
  Utilities.addXUnitDotNETResults(myJob, '**/xUnitResults/*.xml')
  addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
  }

// Determinism
commitPullList.each { isPr ->
  def jobName = Utilities.getFullJobName(projectName, "windows_determinism", isPr)
  def myJob = job(jobName) {
    description('Determinism tests')
    steps {
      batchFile(""".\\build\\scripts\\cibuild.cmd -testDeterminism""")
    }
  }

  def triggerPhraseOnly = false
  def triggerPhraseExtra = "determinism"
  Utilities.setMachineAffinity(myJob, windowsUnitTestMachine)
  addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
}

// Build correctness tests
commitPullList.each { isPr ->
  def jobName = Utilities.getFullJobName(projectName, "windows_build_correctness", isPr)
  def myJob = job(jobName) {
    description('Build correctness tests')
    steps {
      batchFile(""".\\build\\scripts\\test-build-correctness.cmd -cibuild -release""")
    }
  }

  def triggerPhraseOnly = false
  def triggerPhraseExtra = ""
  Utilities.setMachineAffinity(myJob, windowsUnitTestMachine)
  addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
}

// Microbuild
commitPullList.each { isPr ->
  def jobName = Utilities.getFullJobName(projectName, "microbuild", isPr)
  def myJob = job(jobName) {
    description('MicroBuild test')
    steps {
      batchFile(""".\\src\\Tools\\MicroBuild\\cibuild.cmd""")
    }
  }

  def triggerPhraseOnly = false
  def triggerPhraseExtra = "microbuild"
  Utilities.setMachineAffinity(myJob, windowsUnitTestMachine)
  addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
}

// VS Integration Tests
commitPullList.each { isPr ->
  ['debug', 'release'].each { configuration ->
    ['vs-integration'].each { buildTarget ->
      def jobName = Utilities.getFullJobName(projectName, "windows_${configuration}_${buildTarget}", isPr)
      def myJob = job(jobName) {
        description("Windows ${configuration} tests on ${buildTarget}")
        steps {
          batchFile(""".\\build\\scripts\\cibuild.cmd -${configuration} -testVsi""")
        }
      }

      def triggerPhraseOnly = false
      def triggerPhraseExtra = ""
      Utilities.setMachineAffinity(myJob, 'Windows.10.Amd64.ClientRS4.DevEx.15.8.Open')
      Utilities.addXUnitDotNETResults(myJob, '**/xUnitResults/*.xml')
      addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
    }
  }
}

// Loc status check
commitPullList.each { isPr ->
  // Add the check for PR builds, and dev15.8.x/dev15.8.x-vs-deps builds.
  if (isPr
      || branchName == "dev15.8.x"
      || branchName == "dev15.8.x-vs-deps") {
    def jobName = Utilities.getFullJobName(projectName, "windows_loc_status", isPr)
    def myJob = job(jobName) {
      description('Check for untranslated resources')
      steps {
        batchFile(""".\\build\\scripts\\check-loc-status.cmd""")
      }
    }

    // Run it automatically on CI builds but only when requested on PR builds.
    def triggerPhraseOnly = isPr
    def triggerPhraseExtra = "loc"
    Utilities.setMachineAffinity(myJob, windowsUnitTestMachine)
    addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
  }
}

// Loc change check
commitPullList.each { isPr ->
  // This job blocks PRs with loc changes, so only activate it for PR builds of
  // release branches after a loc freeze.
  if (isPr
      && (branchName == "dev15.8.x"
          || branchName == "dev15.8.x-vs-deps")) {
    def jobName = Utilities.getFullJobName(projectName, "windows_loc_changes", isPr)
    def myJob = job(jobName) {
        description('Validate that a PR contains no localization changes')
        steps {
            batchFile(""".\\build\\scripts\\check-for-loc-changes.cmd -base origin/${branchName} -head %GIT_COMMIT%""")
        }
    }

    def triggerPhraseOnly = false
    def triggerPhraseExtra = "loc changes"
    Utilities.setMachineAffinity(myJob, windowsUnitTestMachine)
    addRoslynJob(myJob, jobName, branchName, isPr, triggerPhraseExtra, triggerPhraseOnly)
  }
}

JobReport.Report.generateJobReport(out)

// Make the call to generate the help job
Utilities.createHelperJob(this, projectName, branchName,
    "Welcome to the ${projectName} Repository",  // This is prepended to the help message
    "Have a nice day!")  // This is appended to the help message.  You might put known issues here.
