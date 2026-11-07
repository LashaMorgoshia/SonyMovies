package ge.kadri.tv;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Public catalogue sections and genre pages on MyKadri. */
final class CatalogCategory {
    final String title, path;
    final boolean genre;

    private CatalogCategory(String title, String path, boolean genre) {
        this.title=title;this.path=path;this.genre=genre;
    }
    private static CatalogCategory section(String title,String path){return new CatalogCategory(title,path,false);}
    private static CatalogCategory genre(String title,String slug){return new CatalogCategory(title,"filmebiqartulad/"+slug+"/",true);}
    static final CatalogCategory ALL=section("ყველა","");
    static final List<CatalogCategory> ITEMS=Collections.unmodifiableList(Arrays.asList(
        ALL,
        section("ფილმები","filmebiqartulad/"),
        section("სერიალები","serialebi_qartulad/"),
        section("ანიმე","anime/"),
        section("თურქული სერიალები","turquli-serialebi-qartulad/"),
        genre("ანიმაციური","animaciuri"),
        genre("კომედია","komedia"),
        genre("დრამა","drama"),
        genre("მძაფრსიუჟეტი","mdzafrisiujeti"),
        genre("სათავგადასავლო","tavgadasavali"),
        genre("კრიმინალური","kriminaluri"),
        genre("ტრილერი","trileri"),
        genre("საშინელება","sashineleba"),
        genre("ფანტასტიკა","fantastika"),
        genre("საოჯახო","saojaxo"),
        genre("რომანტიკული","romantikuli"),
        genre("დოკუმენტური","dokumenturi"),
        genre("ბიოგრაფია","biografia"),
        genre("ისტორიული","istoriuli")
    ));
}
