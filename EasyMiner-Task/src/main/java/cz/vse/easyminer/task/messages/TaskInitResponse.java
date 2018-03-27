package cz.vse.easyminer.task.messages;

/**
 * The mining actor sends this message after request on a task initialization: TastInitRequest
 */
public interface TaskInitResponse extends TaskMessageResponse {

    /**
     * This method return status whether the task has been accepted and is running or not.
     *
     * @return true = task has been accepted and is running, false = task has not been accepted (getMessage method returns a reason).
     */
    Boolean isAccepted();

}
