package cz.vse.easyminer.task.messages;

/**
 * The mining actor sends this message after request on a status: TastStatusRequest
 * There are 4 options of behaviour:
 * 1. Task is still running (isActive = true, isCompleted = false, isSuccessful = false, getResult = empty)
 * 2. Task has been failed (isActive = true, isCompleted = true, isSuccessful = false, getResult = empty, getMessage = error message)
 * 3. Task has been successfully completed (isActive = true, isCompleted = true, isSuccessful = true, getResult = result)
 * 4. Task does not exist (isActive = false, isCompleted = false, isSuccessful = false, getResult = empty)
 */
public interface TaskStatusResponse extends TaskMessageResponse {

    /**
     * Task is still active (is running or completed)
     *
     * @return boolean
     */
    Boolean isActive();

    /**
     * Task is completed
     *
     * @return boolean
     */
    Boolean isCompleted();

    /**
     * Task is successfully completed
     *
     * @return true = getResult method returns a result, false = process threw an exception and getMessage method returns an error message
     */
    Boolean isSuccessful();

    /**
     * If this task has been successfully completed, then this method returns result; otherwise it returns the empty array.
     *
     * @return byte array with a result
     */
    Byte[] getResult();

}
