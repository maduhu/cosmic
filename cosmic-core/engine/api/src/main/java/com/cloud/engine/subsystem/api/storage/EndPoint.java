package com.cloud.engine.subsystem.api.storage;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.framework.async.AsyncCompletionCallback;

public interface EndPoint {
    long getId();

    String getHostAddr();

    String getPublicAddr();

    Answer sendMessage(Command cmd);

    void sendMessageAsync(Command cmd, AsyncCompletionCallback<Answer> callback);
}
