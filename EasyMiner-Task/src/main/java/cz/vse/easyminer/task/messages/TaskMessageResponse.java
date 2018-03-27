package cz.vse.easyminer.task.messages;

/**
 * Interface for all task responses from the mining actor to the main controller
 */
interface TaskMessageResponse extends TaskMessage {

    /**
     * Message for this response (status or error messages)
     *
     * @return status message
     */
    String getMessage();

}
