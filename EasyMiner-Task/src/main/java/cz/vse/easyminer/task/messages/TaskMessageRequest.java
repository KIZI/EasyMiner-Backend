package cz.vse.easyminer.task.messages;

import java.util.UUID;

/**
 * Interface for all task requests from the main controller to a mining actor
 */
interface TaskMessageRequest extends TaskMessage {

    /**
     * Task id
     *
     * @return java uuid
     */
    UUID getId();

    /**
     * User EasyMiner API key (we can use it for getting user datasets)
     *
     * @return string
     */
    String getApiKey();

}