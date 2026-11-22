package ma.agencysuite.tools.pdfpoc;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.TextDirection;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Banc d'essai du rendu PDF (ADR-13) : la meme facture fictive, en francais et en
 * arabe, rendue par deux moteurs. Les PDF sont ecrits dans target/pdf-poc/ pour
 * comparaison visuelle, avec le temps de rendu de chaque moteur.
 *
 * <p>Police : Arial de Windows par defaut (elle contient les glyphes arabes).
 * Autre police : -Dpoc.font=C:\chemin\police.ttf
 */
public final class PdfPoc {

    private static final Path OUTPUT_DIR = Path.of(System.getProperty("poc.output", "target/pdf-poc"));
    private static final List<String> LANGUAGES = List.of("fr", "ar");

    private PdfPoc() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        Path font = Path.of(System.getProperty("poc.font", "C:\\Windows\\Fonts\\arial.ttf"));
        if (!Files.exists(font)) {
            throw new IllegalStateException("Police introuvable : " + font + " (indiquez -Dpoc.font=...)");
        }

        System.out.println("== Candidat 1 : Chromium (Playwright) ==");
        System.out.println("   Premier lancement : Playwright telecharge Chromium (~150 Mo) dans votre profil utilisateur.");
        renderWithChromium();

        System.out.println("== Candidat 2 : openhtmltopdf ==");
        renderWithOpenHtmlToPdf(font);

        System.out.println();
        System.out.println("PDF generes dans " + OUTPUT_DIR.toAbsolutePath());
    }

    private static void renderWithChromium() throws IOException {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch()) {
            for (String lang : LANGUAGES) {
                String html = loadTemplate(lang);
                Path out = OUTPUT_DIR.resolve("chromium-" + lang + ".pdf");
                long start = System.nanoTime();
                Page page = browser.newPage();
                page.setContent(html);
                page.pdf(new Page.PdfOptions().setPath(out).setFormat("A4").setPrintBackground(true));
                page.close();
                report(out, start);
            }
        }
    }

    private static void renderWithOpenHtmlToPdf(Path font) throws IOException {
        for (String lang : LANGUAGES) {
            String html = loadTemplate(lang);
            Path out = OUTPUT_DIR.resolve("openhtmltopdf-" + lang + ".pdf");
            long start = System.nanoTime();
            try (OutputStream os = Files.newOutputStream(out)) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.useFont(font.toFile(), "Arial");
                // Decoupage bidirectionnel, reordonnancement et mise en forme des lettres arabes (ICU4J)
                builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
                builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
                builder.defaultTextDirection("ar".equals(lang) ? TextDirection.RTL : TextDirection.LTR);
                builder.withHtmlContent(html, null);
                builder.toStream(os);
                builder.run();
            }
            report(out, start);
        }
    }

    private static String loadTemplate(String lang) throws IOException {
        String resource = "/invoice-" + lang + ".html";
        try (InputStream in = PdfPoc.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Modele introuvable : " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void report(Path out, long startNanos) throws IOException {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        System.out.printf("   %-26s %5d ms  %7d octets%n", out.getFileName(), ms, Files.size(out));
    }
}
