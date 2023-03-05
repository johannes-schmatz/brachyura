package io.github.coolcrabs.brachyura.processing;

@FunctionalInterface
public interface ProcessingSource {
    void getInputs(ProcessingSink sink);
}
