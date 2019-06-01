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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.gitlab4j.api.Constants.TokenType;
import se.bjurr.violations.comments.lib.ViolationsLogger;
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

  public void main(final String args[]) throws Exception {
    final Argument<?> helpArgument = helpArgument("-h", "--help");
    final String parsersString =
        Arrays.asList(Parser.values())
            .stream()
            .map((it) -> it.toString())
            .collect(Collectors.joining(", "));
    final Argument<List<List<String>>> violationsArg =
        stringArgument("--violations", "-v")
            .variableArity()
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
                "Can be the string or the number. Like 'tomas.bjerre85%2Fviolations-test' or '2732496'")
            .build();
    final Argument<String> mergeRequestIidArg =
        stringArgument("-mr-iid").description("Merge Request IID").required().build();
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

      if (parsed.wasGiven(showDebugInfo)) {
        System.out.println(
            "Given parameters:\n"
                + Arrays.asList(args)
                    .stream()
                    .map((it) -> it.toString())
                    .collect(Collectors.joining(", "))
                + "\n\nParsed parameters:\n"
                + this.toString());
      }

    } catch (final ArgumentException exception) {
      System.out.println(exception.getMessageAndUsage());
      System.exit(1);
    }

    if (mergeRequestIid == null || mergeRequestIid.isEmpty()) {
      System.out.println(
          "No merge request iid defined, will not send violation comments to GitLab.");
      return;
    }

    System.out.println(
        "Will comment project " + projectId + " and MR " + mergeRequestIid + " on " + gitLabUrl);

    List<Violation> allParsedViolations = new ArrayList<>();
    for (final List<String> configuredViolation : violations) {
      final String reporter = configuredViolation.size() >= 4 ? configuredViolation.get(3) : null;

      final List<Violation> parsedViolations =
          violationsApi() //
              .findAll(Parser.valueOf(configuredViolation.get(0))) //
              .inFolder(configuredViolation.get(1)) //
              .withPattern(configuredViolation.get(2)) //
              .withReporter(reporter) //
              .violations();
      if (minSeverity != null) {
        allParsedViolations = Filtering.withAtLEastSeverity(allParsedViolations, minSeverity);
      }
      allParsedViolations.addAll(parsedViolations);
    }

    try {
      final TokenType tokenType = apiTokenPrivate ? TokenType.PRIVATE : TokenType.ACCESS;
      final Integer mergeRequestIidInteger = Integer.parseInt(mergeRequestIid);
      violationCommentsToGitLabApi() //
          .setHostUrl(gitLabUrl) //
          .setProjectId(projectId) //
          .setMergeRequestIid(mergeRequestIidInteger) //
          .setApiToken(apiToken) //
          .setTokenType(tokenType) //
          .setCommentOnlyChangedContent(commentOnlyChangedContent) //
          .setCreateCommentWithAllSingleFileComments(createCommentWithAllSingleFileComments) //
          .setCreateSingleFileComments(createSingleFileComments) //
          .setIgnoreCertificateErrors(ignoreCertificateErrors) //
          .setViolations(allParsedViolations) //
          .setShouldKeepOldComments(keepOldComments) //
          .setShouldSetWIP(shouldSetWip) //
          .setCommentTemplate(commentTemplate) //
          .setProxyServer(proxyServer) //
          .setProxyUser(proxyUser) //
          .setProxyPassword(proxyPassword) //
          .setMaxNumberOfViolations(maxNumberOfComments) //
          .setViolationsLogger(
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
              }) //
          .toPullRequest();
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public String toString() {
    return "Runner [violations="
        + violations
        + ", commentOnlyChangedContent="
        + commentOnlyChangedContent
        + ", createCommentWithAllSingleFileComments="
        + createCommentWithAllSingleFileComments
        + ", createSingleFileComments="
        + createSingleFileComments
        + ", gitLabUrl="
        + gitLabUrl
        + ", apiToken="
        + apiToken
        + ", projectId="
        + projectId
        + ", mergeRequestIid="
        + mergeRequestIid
        + ", ignoreCertificateErrors="
        + ignoreCertificateErrors
        + ", apiTokenPrivate="
        + apiTokenPrivate
        + ", minSeverity="
        + minSeverity
        + ", keepOldComments="
        + keepOldComments
        + ", shouldSetWip="
        + shouldSetWip
        + ", commentTemplate="
        + commentTemplate
        + ", proxyServer="
        + proxyServer
        + ", proxyUser="
        + proxyUser
        + ", proxyPassword="
        + proxyPassword
        + ", maxNumberOfComments="
        + maxNumberOfComments
        + "]";
  }
}
