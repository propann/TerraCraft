package com.terracraft.geo;

import java.util.ArrayList;
import java.util.List;

/** Pagination prudente pour les glyphes proportionnels du livre Minecraft. */
final class BookPages {
    static List<String> paginate(List<String> sections) {
        List<String> pages = new ArrayList<>();
        for (String section : sections) {
            List<String> lines = new ArrayList<>();
            for (String paragraph : section.split("\n", -1)) {
                String rest = paragraph;
                while (rest.length() > 18) {
                    int end = rest.lastIndexOf(' ', 18);
                    if (end < 1) end = 18;
                    lines.add(rest.substring(0,end));
                    rest = rest.substring(end).stripLeading();
                }
                lines.add(rest);
            }
            for (int at = 0; at < lines.size(); at += 13) {
                pages.add(String.join("\n",lines.subList(at, Math.min(lines.size(),at+13))));
            }
        }
        if (pages.size() > 100) throw new IllegalArgumentException("Livre trop long : " + pages.size());
        return List.copyOf(pages);
    }
    private BookPages() { }
}
