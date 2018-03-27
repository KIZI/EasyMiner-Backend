package cz.vse.easyminer.task.messages;

import java.util.Map;

/**
 * The main controller sends this message as a request for a creation of a new task.
 * A receiving mining actor reacts on this message and sends TaskInitResponse back to the main controller.
 */
public interface TaskInitRequest extends TaskMessageRequest {

    /**
     * Max running time for the task progress. After reaching of this threshold the task should be killed.
     * @return time in minutes
     */
    Integer getMaxRunningTime();

    /**
     * Task properties
     * @return key-value pairs
     */
    Map<String, String> getProperties();

    /**
     * The main body of the task initialization.
     * It may be empty if there are no purpose for this usage.
     * @return task body with dataset or other settings, or empty
     */
    Byte[] getBody();

}