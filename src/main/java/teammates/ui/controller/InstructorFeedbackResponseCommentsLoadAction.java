package teammates.ui.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import teammates.common.datatransfer.CourseRoster;
import teammates.common.datatransfer.FeedbackSessionResultsBundle;
import teammates.common.datatransfer.attributes.FeedbackQuestionAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseCommentAttributes;
import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.util.Assumption;
import teammates.common.util.Const;
import teammates.ui.pagedata.InstructorFeedbackResponseCommentsLoadPageData;

public class InstructorFeedbackResponseCommentsLoadAction extends Action {

    private static final Boolean IS_INCLUDE_RESPONSE_STATUS = true;
    private InstructorAttributes instructor;

    @Override
    protected ActionResult execute() throws EntityDoesNotExistException {
        String courseId = getRequestParamValue(Const.ParamsNames.COURSE_ID);
        Assumption.assertPostParamNotNull(Const.ParamsNames.COURSE_ID, courseId);

        String fsName = getRequestParamValue(Const.ParamsNames.FEEDBACK_SESSION_NAME);
        Assumption.assertPostParamNotNull(Const.ParamsNames.FEEDBACK_SESSION_NAME, fsName);

        String fsIndexString = getRequestParamValue(Const.ParamsNames.FEEDBACK_SESSION_INDEX);
        Assumption.assertPostParamNotNull(Const.ParamsNames.FEEDBACK_SESSION_INDEX, fsIndexString);
        int fsIndex = 0;
        try {
            fsIndex = Integer.parseInt(fsIndexString);
        } catch (NumberFormatException e) {
            Assumption.fail("Invalid request parameter value for feedback session index: " + fsIndexString);
        }

        instructor = logic.getInstructorForGoogleId(courseId, account.googleId);

        gateKeeper.verifyAccessible(instructor, logic.getCourse(courseId));

        CourseRoster roster = new CourseRoster(logic.getStudentsForCourse(courseId),
                                               logic.getInstructorsForCourse(courseId));

        FeedbackSessionResultsBundle bundle = getFeedbackResultBundle(courseId, fsName, roster);
        InstructorFeedbackResponseCommentsLoadPageData data =
                new InstructorFeedbackResponseCommentsLoadPageData(account, sessionToken, fsIndex, instructor, bundle);
        return createShowPageResult(Const.ViewURIs.INSTRUCTOR_FEEDBACK_RESPONSE_COMMENTS_LOAD, data);
    }

    private FeedbackSessionResultsBundle getFeedbackResultBundle(String courseId, String fsname,
            CourseRoster roster) throws EntityDoesNotExistException {
        FeedbackSessionResultsBundle bundle =
                logic.getFeedbackSessionResultsForInstructor(
                        fsname, courseId, instructor.email, roster, !IS_INCLUDE_RESPONSE_STATUS);
        removeQuestionsAndResponsesIfNotAllowed(bundle);
        removeQuestionsAndResponsesWithoutFeedbackResponseComment(bundle);

        return bundle.questions.isEmpty() ? null : bundle;
    }

    private void removeQuestionsAndResponsesIfNotAllowed(FeedbackSessionResultsBundle bundle) {
        Iterator<FeedbackResponseAttributes> iter = bundle.responses.iterator();
        while (iter.hasNext()) {
            FeedbackResponseAttributes fdr = iter.next();
            boolean canInstructorViewSessionInGiverSection =
                    instructor.isAllowedForPrivilege(fdr.giverSection, fdr.feedbackSessionName,
                                       Const.ParamsNames.INSTRUCTOR_PERMISSION_VIEW_SESSION_IN_SECTIONS);
            boolean canInstructorViewSessionInRecipientSection =
                    instructor.isAllowedForPrivilege(fdr.recipientSection, fdr.feedbackSessionName,
                                       Const.ParamsNames.INSTRUCTOR_PERMISSION_VIEW_SESSION_IN_SECTIONS);

            boolean hasSessionViewingPrivileges = canInstructorViewSessionInGiverSection
                                                            && canInstructorViewSessionInRecipientSection;
            if (!hasSessionViewingPrivileges) {
                iter.remove();
            }
        }
    }

    private void removeQuestionsAndResponsesWithoutFeedbackResponseComment(FeedbackSessionResultsBundle bundle) {
        List<FeedbackResponseAttributes> responsesWithFeedbackResponseComment = new ArrayList<>();
        for (FeedbackResponseAttributes fr : bundle.responses) {
            List<FeedbackResponseCommentAttributes> frComment = bundle.responseComments.get(fr.getId());
            if (frComment != null && !frComment.isEmpty()) {
                responsesWithFeedbackResponseComment.add(fr);
            }
        }
        Map<String, FeedbackQuestionAttributes> questionsWithFeedbackResponseComment = new HashMap<>();
        for (FeedbackResponseAttributes fr : responsesWithFeedbackResponseComment) {
            FeedbackQuestionAttributes qn = bundle.questions.get(fr.feedbackQuestionId);
            if (!questionsWithFeedbackResponseComment.containsKey(qn.getId())) {
                questionsWithFeedbackResponseComment.put(qn.getId(), qn);
            }
        }
        bundle.questions = questionsWithFeedbackResponseComment;
        bundle.responses = responsesWithFeedbackResponseComment;
    }
}
