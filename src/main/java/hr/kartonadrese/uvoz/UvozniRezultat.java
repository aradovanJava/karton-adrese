package hr.kartonadrese.uvoz;

import hr.kartonadrese.core.uvoz.UvozniZapis;

import java.time.LocalDate;
import java.util.List;

/** Rezultat čitanja jednog izvora prije usklađivanja i spremanja. */
public record UvozniRezultat(List<UvozniZapis> zapisi, int procitano, int bezNaziva, int bezLokacije,
                             LocalDate datumPodatka, String napomena) {
}
