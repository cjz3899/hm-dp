package com.junzhecai.hmdp.utils;

public interface GlobalLock {
    boolean tryLock(long timeoutSec);

    void unlock();
}
