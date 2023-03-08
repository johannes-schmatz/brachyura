package io.github.coolcrabs.brachyura.compiler.java;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

import org.tinylog.Logger;

import java.util.function.Consumer;

class BrachyuraDiagnosticListener implements DiagnosticListener<JavaFileObject> {
    private final Consumer<Diagnostic<? extends JavaFileObject>> onDiagnostic;
    public BrachyuraDiagnosticListener(Consumer<Diagnostic<? extends JavaFileObject>> onDiagnostic) {
        this.onDiagnostic = onDiagnostic;
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        onDiagnostic.accept(diagnostic);

        String line = diagnostic.toString();
        switch (diagnostic.getKind()) {
            case ERROR:
                Logger.error(line);
                break;
            case WARNING:
            case MANDATORY_WARNING:
                Logger.warn(line);
                break;
            case NOTE:
            case OTHER:
            default:
                Logger.info(line);
                break;
        }
    }
}
