package io.github.coolcrabs.brachyura.project;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.github.coolcrabs.brachyura.exception.TaskFailedException;

public interface Task {

    static Task of(String name, BooleanSupplier run) {
        return new FailableNoArgTask(name, run);
    }

    static Task of(String name, Runnable run) {
        return new NoArgTask(name, run);
    }

    static Task of(String name, Consumer<String[]> run) {
        return new TaskWithArgs(name, run);
    }


    void doTask(String[] args);

    String getName();

    abstract class AbstractTask implements Task {
        public final String name;

        public AbstractTask(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    class FailableNoArgTask extends AbstractTask {
        public final BooleanSupplier runnable;

        FailableNoArgTask(String name, BooleanSupplier runnable) {
            super(name);
            this.runnable = runnable;
        }

        @Override
        public void doTask(String[] args) {
            if (!runnable.getAsBoolean()) throw new TaskFailedException("Task returned false");
        }
    }

    class NoArgTask extends AbstractTask {
        public final Runnable runnable;

        NoArgTask(String name, Runnable runnable) {
            super(name);
            this.runnable = runnable;
        }

        @Override
        public void doTask(String[] args) {
            runnable.run();
        }
    }

    class TaskWithArgs extends AbstractTask {
        public final Consumer<String[]> task;

        TaskWithArgs(String name, Consumer<String[]> task) {
            super(name);
            this.task = task;
        }

        @Override
        public void doTask(String[] args) {
            task.accept(args);
        }
    }
}
