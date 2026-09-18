package se.bjurr.violations.main;

import static java.lang.Integer.MAX_VALUE;
import static se.bjurr.violations.comments.gitlab.lib.ViolationCommentsToGitLabApi.violationCommentsToGitLabApi;
import static se.bjurr.violations.lib.ViolationsApi.violationsApi;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import org.gitlab4j.models.Constants.TokenType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import se.bjurr.violations.lib.FilteringViolationsLogger;
import se.bjurr.violations.lib.ViolationsLogger;
import se.bjurr.violations.lib.model.SEVERITY;
import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.reports.Parser;
import se.bjurr.violations.lib.util.Filtering;

@Command(name = "violation-comments-to-gitlab-command-line")
public class Runner implements Runnable {

  @Option(
      names = {"--violations", "-v"},
      parameterConsumer = ViolationsArgConverter.class,
      description =
          "The violations to look for. <PARSER> <FOLDER> <REGEXP PATTERN> <NAME> where PARSER is"
              + " one of the se.bjurr.violations.lib.reports.Parser enum values.\n"
              + " Example: -v \"JSHINT\" \".\" \".*/jshint.xml$\" \"JSHint\"",
      arity = "4")
  List<List<String>> violationsArg = new ArrayList<>();

  @Option(
      names = {"-severity", "-s"},
      defaultValue = "INFO",
      description = "Minimum severity level to report. ${COMPLETION-CANDIDATES}")
  SEVERITY minSeverityArg;

  @Option(
      names = "-show-debug-info",
      description =
          "Also logs every GitLab API request and response, headers included. Off by default,"
              + " since it's noisy. Please run your command with this parameter and supply output"
              + " when reporting bugs.")
  boolean showDebugInfoArg; // NOPMD only used within run(), kept as a field for readability

  @Option(
      names = {"-comment-only-changed-content", "-cocc"},
      arity = "1",
      defaultValue = "true")
  boolean commentOnlyChangedContentArg;

  @Option(
      names = {"-comment-only-changed-content-context", "-coccc"},
      defaultValue = "0")
  Integer commentOnlyChangedContentContextArg;

  @Option(
      names = {"-comment-only-changed-files", "-cocf"},
      arity = "1",
      defaultValue = "true",
      description =
          "True if only changed files should be commented. False if all findings should be"
              + " commented.")
  boolean commentOnlyChangedFilesArg;

  @Option(
      names = {"-create-comment-with-all-single-file-comments", "-ccwasfc"},
      arity = "1",
      defaultValue = "false")
  boolean createCommentWithAllSingleFileCommentsArg;

  @Option(
      names = {"-create-single-file-comments", "-csfc"},
      arity = "1",
      defaultValue = "true")
  boolean createSingleFileCommentsArg;

  @Option(
      names = {"-gitlab-url", "-gu"},
      defaultValue = "https://gitlab.com/")
  String gitLabUrlArg;

  @Option(
      names = {"-api-token", "-at"},
      required = true)
  String apiTokenArg;

  @Option(
      names = {"-project-id", "-pi"},
      description =
          "Can be the string or the number. Like 'tomas.bjerre85/violations-test' or '2732496'")
  String projectIdArg;

  @Option(
      names = "-mr-iid",
      required = true,
      description = {"Merge Request IID", "Example: 1"})
  String mergeRequestIidArg;

  @Option(names = "-ignore-certificate-errors", arity = "1", defaultValue = "true")
  boolean ignoreCertificateErrorsArg;

  @Option(names = "-api-token-private", arity = "1", defaultValue = "true")
  boolean apiTokenPrivateArg;

  @Option(names = "-keep-old-comments", arity = "1", defaultValue = "false")
  boolean keepOldCommentsArg;

  @Option(
      names = "-create-comments-as-resolvable-threads",
      arity = "1",
      defaultValue = "false",
      description =
          "True if the general comment should be posted as a resolvable discussion thread,"
              + " rather than a plain note. GitLab has no separate \"task\" concept, but a"
              + " discussion thread can be marked resolved, the same as a diff comment's thread"
              + " already is.")
  boolean createCommentsAsResolvableThreadsArg;

  @Option(names = "-should-set-wip", arity = "1", defaultValue = "false")
  boolean shouldSetWipArg;

  @Option(
      names = "-comment-template",
      defaultValue = "",
      description = "https://github.com/tomasbjerre/violation-comments-lib")
  String commentTemplateArg;

  @Option(names = "-proxy-server", defaultValue = "")
  String proxyServerArg;

  @Option(names = "-proxy-user", defaultValue = "")
  String proxyUserArg;

  @Option(names = "-proxy-password", defaultValue = "")
  String proxyPasswordArg;

  @Option(
      names = {"-max-number-of-comments", "-mnoc"},
      defaultValue = MAX_VALUE + "")
  Integer maxNumberOfCommentsArg;

  @Option(names = "--help", usageHelp = true, description = "display this help and exit")
  boolean helpArg; // NOPMD picocli-managed, only used by the framework

  @Override
  public void run() {
    if (this.showDebugInfoArg) {
      System.out.println( // NOPMD stdout is the CLI output
          "Parsed parameters:\n" + this.toString());
    }

    ViolationsLogger violationsLogger =
        new ViolationsLogger() {
          @Override
          public void log(final Level level, final String string) {
            System.out.println(level + " " + string); // NOPMD stdout is the CLI output
          }

          @Override
          @SuppressFBWarnings(
              value = "INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE",
              justification =
                  "This is a command line tool, the stack trace is meant to be seen by the user"
                      + " running it, not exposed to a remote party.")
          public void log(final Level level, final String string, final Throwable t) {
            final StringWriter sw = new StringWriter();
            t.printStackTrace(
                new PrintWriter(sw)); // NOPMD writes to an in-memory buffer, not System.err
            System.out.println( // NOPMD stdout is the CLI output
                level + " " + string + "\n" + sw.toString());
          }
        };
    if (!this.showDebugInfoArg) {
      violationsLogger = FilteringViolationsLogger.filterLevel(violationsLogger);
    }

    if (this.mergeRequestIidArg == null || this.mergeRequestIidArg.isEmpty()) {
      System.out.println( // NOPMD stdout is the CLI output
          "No merge request iid defined, will not send violation comments to GitLab.");
      return;
    }

    System.out.println( // NOPMD stdout is the CLI output
        "Will comment project "
            + this.projectIdArg
            + " and MR "
            + this.mergeRequestIidArg
            + " on "
            + this.gitLabUrlArg);

    Set<Violation> allParsedViolations = new TreeSet<>();
    for (final List<String> configuredViolation : this.violationsArg) {
      final String reporter = configuredViolation.size() >= 4 ? configuredViolation.get(3) : null;

      final Set<Violation> parsedViolations =
          violationsApi() //
              .withViolationsLogger(violationsLogger) //
              .findAll(Parser.valueOf(configuredViolation.get(0))) //
              .inFolder(configuredViolation.get(1)) //
              .withPattern(configuredViolation.get(2)) //
              .withReporter(reporter) //
              .violations();
      if (this.minSeverityArg != null) {
        allParsedViolations =
            Filtering.withAtLEastSeverity(allParsedViolations, this.minSeverityArg);
      }
      allParsedViolations.addAll(parsedViolations);
    }

    try {
      final TokenType tokenType = this.apiTokenPrivateArg ? TokenType.PRIVATE : TokenType.ACCESS;
      final Long mergeRequestIidInteger = Long.parseLong(this.mergeRequestIidArg);
      violationCommentsToGitLabApi()
          .setHostUrl(this.gitLabUrlArg)
          .setProjectId(this.projectIdArg)
          .setMergeRequestIid(mergeRequestIidInteger)
          .setApiToken(this.apiTokenArg)
          .setTokenType(tokenType)
          .setCommentOnlyChangedContent(this.commentOnlyChangedContentArg) //
          .setCommentOnlyChangedContentContext(this.commentOnlyChangedContentContextArg) //
          .withShouldCommentOnlyChangedFiles(this.commentOnlyChangedFilesArg) //
          .setCreateCommentWithAllSingleFileComments(
              this.createCommentWithAllSingleFileCommentsArg) //
          .setCreateSingleFileComments(this.createSingleFileCommentsArg) //
          .setIgnoreCertificateErrors(this.ignoreCertificateErrorsArg) //
          .setViolations(allParsedViolations) //
          .setShouldKeepOldComments(this.keepOldCommentsArg) //
          .withCreateCommentsAsResolvableThreads(this.createCommentsAsResolvableThreadsArg) //
          .setShouldSetWIP(this.shouldSetWipArg) //
          .setCommentTemplate(this.commentTemplateArg) //
          .setLogRequestResponse(this.showDebugInfoArg) //
          .setProxyServer(this.proxyServerArg) //
          .setProxyUser(this.proxyUserArg) //
          .setProxyPassword(this.proxyPasswordArg) //
          .setMaxNumberOfViolations(this.maxNumberOfCommentsArg) //
          .setViolationsLogger(violationsLogger) //
          .toPullRequest();
    } catch (final Exception e) {
      e.printStackTrace(); // NOPMD top-level CLI error handler
    }
  }

  @Override
  public String toString() {
    return "Runner [violations="
        + this.violationsArg
        + ", commentOnlyChangedContent="
        + this.commentOnlyChangedContentArg
        + ", commentOnlyChangedContentContext="
        + this.commentOnlyChangedContentContextArg
        + ", commentOnlyChangedFiles="
        + this.commentOnlyChangedFilesArg
        + ", createCommentWithAllSingleFileComments="
        + this.createCommentWithAllSingleFileCommentsArg
        + ", createSingleFileComments="
        + this.createSingleFileCommentsArg
        + ", gitLabUrl="
        + this.gitLabUrlArg
        + ", apiToken="
        + this.apiTokenArg
        + ", projectId="
        + this.projectIdArg
        + ", mergeRequestIid="
        + this.mergeRequestIidArg
        + ", ignoreCertificateErrors="
        + this.ignoreCertificateErrorsArg
        + ", apiTokenPrivate="
        + this.apiTokenPrivateArg
        + ", minSeverity="
        + this.minSeverityArg
        + ", keepOldComments="
        + this.keepOldCommentsArg
        + ", createCommentsAsResolvableThreads="
        + this.createCommentsAsResolvableThreadsArg
        + ", shouldSetWip="
        + this.shouldSetWipArg
        + ", commentTemplate="
        + this.commentTemplateArg
        + ", proxyServer="
        + this.proxyServerArg
        + ", proxyUser="
        + this.proxyUserArg
        + ", proxyPassword="
        + this.proxyPasswordArg
        + ", maxNumberOfComments="
        + this.maxNumberOfCommentsArg
        + "]";
  }
}
