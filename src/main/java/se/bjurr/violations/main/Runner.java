package se.bjurr.violations.main;

import static java.lang.Integer.MAX_VALUE;
import static se.bjurr.violations.comments.gitlab.lib.ViolationCommentsToGitLabApi.violationCommentsToGitLabApi;
import static se.bjurr.violations.lib.ViolationsApi.violationsApi;
import static se.bjurr.violations.lib.model.SEVERITY.INFO;
import static se.softhouse.jargo.Arguments.booleanArgument;
import static se.softhouse.jargo.Arguments.enumArgument;
import static se.softhouse.jargo.Arguments.helpArgument;
import static se.softhouse.jargo.Arguments.integerArgument;
import static se.softhouse.jargo.Arguments.optionArgument;
import static se.softhouse.jargo.Arguments.stringArgument;
import static se.softhouse.jargo.CommandLineParser.withArguments;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.gitlab4j.api.Constants.TokenType;
import se.bjurr.violations.lib.FilteringViolationsLogger;
import se.bjurr.violations.lib.ViolationsLogger;
import se.bjurr.violations.lib.model.SEVERITY;
import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.reports.Parser;
import se.bjurr.violations.lib.util.Filtering;
import se.softhouse.jargo.Argument;
import se.softhouse.jargo.ArgumentException;
import se.softhouse.jargo.ParsedArguments;

public class Runner {

  private List<List<String>> violations;
  private boolean commentOnlyChangedContent;
  private Integer commentOnlyChangedContentContext;
  private boolean commentOnlyChangedFiles;
  private boolean createCommentWithAllSingleFileComments;
  private boolean createSingleFileComments;
  private String gitLabUrl;
  private String apiToken;
  private String projectId;
  private String mergeRequestIid;
  private Boolean ignoreCertificateErrors;
  private Boolean apiTokenPrivate;
  private SEVERITY minSeverity;
  private Boolean keepOldComments;
  private Boolean shouldSetWip;
  private String commentTemplate;
  private String proxyServer;
  private String proxyUser;
  private String proxyPassword;
  private Integer maxNumberOfComments;
  private boolean showDebugInfo;

  public void main(final String args[]) throws Exception {
    final Argument<?> helpArgument = helpArgument("-h", "--help");
    final String parsersString =
        Arrays.asList(Parser.values()).stream()
            .map((it) -> it.toString())
            .collect(Collectors.joining(", "));
    final Argument<List<List<String>>> violationsArg =
        stringArgument("--violations", "-v")
            .arity(4)
            .repeated()
            .description(
                "The violations to look for. <PARSER> <FOLDER> <REGEXP PATTERN> <NAME> where PARSER is one of: "
                    + parsersString
                    + "\n Example: -v \"JSHINT\" \".\" \".*/jshint.xml$\" \"JSHint\"")
            .build();
    final Argument<SEVERITY> minSeverityArg =
        enumArgument(SEVERITY.class, "-severity", "-s")
            .defaultValue(INFO)
            .description("Minimum severity level to report.")
            .build();
    final Argument<Boolean> showDebugInfo =
        optionArgument("-show-debug-info")
            .description(
                "Please run your command with this parameter and supply output when reporting bugs.")
            .build();

    final Argument<Boolean> commentOnlyChangedContentArg =
        booleanArgument("-comment-only-changed-content", "-cocc").defaultValue(true).build();

    final Argument<Integer> commentOnlyChangedContentContextArg =
        integerArgument("-comment-only-changed-content-context", "-coccc").defaultValue(0).build();

    final Argument<Boolean> shouldCommentOnlyChangedFilesArg =
        booleanArgument("-comment-only-changed-files", "-cocf")
            .defaultValue(true)
            .description(
                "True if only changed files should be commented. False if all findings should be commented.")
            .build();
    final Argument<Boolean> createCommentWithAllSingleFileCommentsArg =
        booleanArgument("-create-comment-with-all-single-file-comments", "-ccwasfc")
            .defaultValue(false)
            .build();
    final Argument<Boolean> createSingleFileCommentsArg =
        booleanArgument("-create-single-file-comments", "-csfc").defaultValue(true).build();
    final Argument<String> gitLabUrlArg =
        stringArgument("-gitlab-url", "-gu").defaultValue("https://gitlab.com/").build();
    final Argument<String> apiTokenArg = stringArgument("-api-token", "-at").required().build();
    final Argument<String> projectIdArg =
        stringArgument("-project-id", "-pi")
            .description(
                "Can be the string or the number. Like 'tomas.bjerre85/violations-test' or '2732496'")
            .build();
    final Argument<String> mergeRequestIidArg =
        stringArgument("-mr-iid")
            .description("Merge Request IID")
            .description("Example: 1")
            .required()
            .build();
    final Argument<Boolean> ignoreCertificateErrorsArg =
        booleanArgument("-ignore-certificate-errors").defaultValue(true).build();
    final Argument<Boolean> apiTokenPrivateArg =
        booleanArgument("-api-token-private").defaultValue(true).build();
    final Argument<Boolean> keepOldCommentsArg =
        booleanArgument("-keep-old-comments").defaultValue(false).build();
    final Argument<Boolean> shouldSetWipArg =
        booleanArgument("-should-set-wip").defaultValue(false).build();
    final Argument<String> commentTemplateArg =
        stringArgument("-comment-template")
            .defaultValue("")
            .description("https://github.com/tomasbjerre/violation-comments-lib")
            .build();
    final Argument<String> proxyServerArg =
        stringArgument("-proxy-server").defaultValue("").build();
    final Argument<String> proxyUserArg = stringArgument("-proxy-user").defaultValue("").build();
    final Argument<String> proxyPasswordArg =
        stringArgument("-proxy-password").defaultValue("").build();
    final Argument<Integer> maxNumberOfCommentsArg =
        integerArgument("-max-number-of-comments", "-mnoc").defaultValue(MAX_VALUE).build();

    try {
      final ParsedArguments parsed =
          withArguments( //
                  helpArgument, //
                  violationsArg, //
                  minSeverityArg, //
                  showDebugInfo, //
                  commentOnlyChangedContentArg, //
                  commentOnlyChangedContentContextArg, //
                  shouldCommentOnlyChangedFilesArg, //
                  createCommentWithAllSingleFileCommentsArg, //
                  createSingleFileCommentsArg, //
                  gitLabUrlArg, //
                  apiTokenArg, //
                  projectIdArg, //
                  mergeRequestIidArg, //
                  ignoreCertificateErrorsArg, //
                  apiTokenPrivateArg, //
                  keepOldCommentsArg, //
                  shouldSetWipArg, //
                  commentTemplateArg, //
                  proxyServerArg, //
                  proxyUserArg, //
                  proxyPasswordArg, //
                  maxNumberOfCommentsArg) //
              .parse(args);

      this.violations = parsed.get(violationsArg);
      this.minSeverity = parsed.get(minSeverityArg);
      this.commentOnlyChangedContent = parsed.get(commentOnlyChangedContentArg);
      this.commentOnlyChangedContentContext = parsed.get(commentOnlyChangedContentContextArg);
      this.commentOnlyChangedFiles = parsed.get(shouldCommentOnlyChangedFilesArg);
      this.createCommentWithAllSingleFileComments =
          parsed.get(createCommentWithAllSingleFileCommentsArg);
      this.createSingleFileComments = parsed.get(createSingleFileCommentsArg);
      this.gitLabUrl = parsed.get(gitLabUrlArg);
      this.apiToken = parsed.get(apiTokenArg);
      this.projectId = parsed.get(projectIdArg);
      this.mergeRequestIid = parsed.get(mergeRequestIidArg);
      this.ignoreCertificateErrors = parsed.get(ignoreCertificateErrorsArg);
      this.apiTokenPrivate = parsed.get(apiTokenPrivateArg);
      this.keepOldComments = parsed.get(keepOldCommentsArg);
      this.shouldSetWip = parsed.get(shouldSetWipArg);
      this.commentTemplate = parsed.get(commentTemplateArg);
      this.proxyServer = parsed.get(proxyServerArg);
      this.proxyUser = parsed.get(proxyUserArg);
      this.proxyPassword = parsed.get(proxyPasswordArg);
      this.maxNumberOfComments = parsed.get(maxNumberOfCommentsArg);
      this.showDebugInfo = parsed.wasGiven(showDebugInfo);
      if (this.showDebugInfo) {
        System.out.println(
            "Given parameters:\n"
                + Arrays.asList(args).stream()
                    .map((it) -> it.toString())
                    .collect(Collectors.joining(", "))
                + "\n\nParsed parameters:\n"
                + this.toString());
      }

    } catch (final ArgumentException exception) {
      System.out.println(exception.getMessageAndUsage());
      System.exit(1);
    }

    ViolationsLogger violationsLogger =
        new ViolationsLogger() {
          @Override
          public void log(final Level level, final String string) {
            System.out.println(level + " " + string);
          }

          @Override
          public void log(final Level level, final String string, final Throwable t) {
            final StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            System.out.println(level + " " + string + "\n" + sw.toString());
          }
        };
    if (!this.showDebugInfo) {
      violationsLogger = FilteringViolationsLogger.filterLevel(violationsLogger);
    }

    if (this.mergeRequestIid == null || this.mergeRequestIid.isEmpty()) {
      System.out.println(
          "No merge request iid defined, will not send violation comments to GitLab.");
      return;
    }

    System.out.println(
        "Will comment project "
            + this.projectId
            + " and MR "
            + this.mergeRequestIid
            + " on "
            + this.gitLabUrl);

    Set<Violation> allParsedViolations = new TreeSet<>();
    for (final List<String> configuredViolation : this.violations) {
      final String reporter = configuredViolation.size() >= 4 ? configuredViolation.get(3) : null;

      final Set<Violation> parsedViolations =
          violationsApi() //
              .withViolationsLogger(violationsLogger) //
              .findAll(Parser.valueOf(configuredViolation.get(0))) //
              .inFolder(configuredViolation.get(1)) //
              .withPattern(configuredViolation.get(2)) //
              .withReporter(reporter) //
              .violations();
      if (this.minSeverity != null) {
        allParsedViolations = Filtering.withAtLEastSeverity(allParsedViolations, this.minSeverity);
      }
      allParsedViolations.addAll(parsedViolations);
    }

    try {
      final TokenType tokenType = this.apiTokenPrivate ? TokenType.PRIVATE : TokenType.ACCESS;
      final Long mergeRequestIidInteger = Long.parseLong(this.mergeRequestIid);
      violationCommentsToGitLabApi()
          .setHostUrl(this.gitLabUrl)
          .setProjectId(this.projectId)
          .setMergeRequestIid(mergeRequestIidInteger)
          .setApiToken(this.apiToken)
          .setTokenType(tokenType)
          .setCommentOnlyChangedContent(this.commentOnlyChangedContent) //
          .setCommentOnlyChangedContentContext(this.commentOnlyChangedContentContext) //
          .withShouldCommentOnlyChangedFiles(this.commentOnlyChangedFiles) //
          .setCreateCommentWithAllSingleFileComments(
              this.createCommentWithAllSingleFileComments) //
          .setCreateSingleFileComments(this.createSingleFileComments) //
          .setIgnoreCertificateErrors(this.ignoreCertificateErrors) //
          .setViolations(allParsedViolations) //
          .setShouldKeepOldComments(this.keepOldComments) //
          .setShouldSetWIP(this.shouldSetWip) //
          .setCommentTemplate(this.commentTemplate) //
          .setProxyServer(this.proxyServer) //
          .setProxyUser(this.proxyUser) //
          .setProxyPassword(this.proxyPassword) //
          .setMaxNumberOfViolations(this.maxNumberOfComments) //
          .setViolationsLogger(violationsLogger) //
          .toPullRequest();
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public String toString() {
    return "Runner [violations="
        + this.violations
        + ", commentOnlyChangedContent="
        + this.commentOnlyChangedContent
        + ", commentOnlyChangedContentContext="
        + this.commentOnlyChangedContentContext
        + ", createCommentWithAllSingleFileComments="
        + this.createCommentWithAllSingleFileComments
        + ", createSingleFileComments="
        + this.createSingleFileComments
        + ", gitLabUrl="
        + this.gitLabUrl
        + ", apiToken="
        + this.apiToken
        + ", projectId="
        + this.projectId
        + ", mergeRequestIid="
        + this.mergeRequestIid
        + ", ignoreCertificateErrors="
        + this.ignoreCertificateErrors
        + ", apiTokenPrivate="
        + this.apiTokenPrivate
        + ", minSeverity="
        + this.minSeverity
        + ", keepOldComments="
        + this.keepOldComments
        + ", shouldSetWip="
        + this.shouldSetWip
        + ", commentTemplate="
        + this.commentTemplate
        + ", proxyServer="
        + this.proxyServer
        + ", proxyUser="
        + this.proxyUser
        + ", proxyPassword="
        + this.proxyPassword
        + ", maxNumberOfComments="
        + this.maxNumberOfComments
        + "]";
  }
}
