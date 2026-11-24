package ma.agencysuite.platform.application;

import ma.agencysuite.shared.module.ModuleCode;

/** Endpoint d'un module non souscrit par l'agence courante : 403, type {@code module-not-enabled}. */
public class ModuleNotEnabledException extends PlatformException {

    private final ModuleCode module;

    public ModuleNotEnabledException(ModuleCode module) {
        super("Module non active pour l'agence courante : " + module);
        this.module = module;
    }

    public ModuleCode getModule() {
        return module;
    }
}
