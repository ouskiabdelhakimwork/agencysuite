package ma.agencysuite.platform.application;

public class PlanNotFoundException extends ResourceNotFoundException {

    public PlanNotFoundException(String planCode) {
        super("Palier introuvable : " + planCode);
    }
}
