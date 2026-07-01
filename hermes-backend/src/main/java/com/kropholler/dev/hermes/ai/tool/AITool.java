package com.kropholler.dev.hermes.ai.tool;

public interface AITool<T> {
    String execute(T param);
}
