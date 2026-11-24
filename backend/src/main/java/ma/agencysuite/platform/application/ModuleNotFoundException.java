package ma.agencysuite.platform.application;

public class ModuleNotFoundException extends ResourceNotFoundException {

    public ModuleNotFoundException(String moduleCode) {
        super("Module introuvable : " + moduleCode);
    }
}
