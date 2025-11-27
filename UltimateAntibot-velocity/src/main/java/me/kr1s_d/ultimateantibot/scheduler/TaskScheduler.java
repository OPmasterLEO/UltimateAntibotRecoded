package me.kr1s_d.ultimateantibot.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.TaskStatus;

public class TaskScheduler {
    private static final Map<Long, ScheduledTask> scheduled = new ConcurrentHashMap<>();
    private static long current = 0;

    public static void cancelTrackedTask(long taskID) {
        checkTasks();
        ScheduledTask task = scheduled.getOrDefault(taskID, null);
        if(task == null) return;
        scheduled.remove(taskID);
        task.cancel();
    }

    public static long trackTask(ScheduledTask task) {
        checkTasks();
        current++;
        scheduled.put(current, task);
        return current;
    }

    private static void checkTasks() {
        scheduled.entrySet().removeIf(entry -> 
            entry.getValue().status() == TaskStatus.CANCELLED || 
            entry.getValue().status() == TaskStatus.FINISHED
        );
    }
}
