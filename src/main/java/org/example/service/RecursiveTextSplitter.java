package org.example.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RecursiveTextSplitter {

    // --- Konfiguration ---
    // Använd en säkrare regex för meningar: matchar punkt/frågetecken/utropstecken följt av whitespace
    private final String[] separators = {"\n\n", "\n", "[.?!]\\s", " "};
    private final int chunkSize;
    private final int chunkOverlap;
    private final int searchRange = 40;

    /**
     * Konstruktor för att specificera chunk-storlek och överlappning.
     * @param chunkSize Maximal storlek för en chunk i antal tecken.
     * @param chunkOverlap Antal tecken som ska överlappas mellan chunks.
     */
    public RecursiveTextSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * Huvudmetoden för att dela upp texten i kontextuella chunks med överlappning.
     * @param text Den råa texten som ska delas upp.
     * @return En lista av textchunks redo för embedding.
     */
    public List<String> splitText(String text) {
        // 1. Kör den rekursiva delningslogiken
        List<String> rawChunks = recursiveSplit(text, Arrays.asList(separators));

        // 2. Filtrera bort eventuella tomma eller whitespace-only chunks
        rawChunks = rawChunks.stream()
                .filter(chunk -> !chunk.trim().isEmpty())
                .collect(Collectors.toList());

        // 3. Applicera överlappningslogiken
        return applyOverlap(rawChunks);
    }

    // ----------------------------------------------------------------------
    // --- Rekursiv Delningslogik (Hittar rena brytpunkter) ---
    // ----------------------------------------------------------------------

    private List<String> recursiveSplit(String text, List<String> currentSeparators) {

        List<String> chunks = new ArrayList<>();
        String remainingText = text;

        while (remainingText.length() > chunkSize) {

            int cutoff = chunkSize;
            int lastCleanBreak = -1;
            String foundSeparator = null;

            // 1. Sök efter den sista rena brytpunkten nära gränsen
            for (String separator : currentSeparators) {

                Pattern pattern = Pattern.compile(separator);

                // Skapa en delsträng som sträcker sig fram till cutoff för att optimera sökningen
                String searchArea = remainingText.substring(0, cutoff);
                Matcher matcher = pattern.matcher(searchArea);

                int currentMatchStart = -1;
                int currentMatchEnd = -1;

                // Hitta den SISTA matchningen i sökregionen
                while (matcher.find()) {
                    currentMatchStart = matcher.start();
                    currentMatchEnd = matcher.end();
                }

                // Vi vill att brytpunkten ska vara relativt nära cutoff (i sista 100 tecknen)
                // Hur långt in från cutoff vi letar efter en ren brytpunkt
                int searchStartThreshold = cutoff - searchRange;

                if (currentMatchStart > searchStartThreshold) {
                    lastCleanBreak = currentMatchEnd;
                    foundSeparator = separator;
                    break; // Vi hittade en bra, högprioriterad brytpunkt!
                }
            }

            // 2. Klipp av vid den rena brytpunkten
            if (lastCleanBreak != -1) {

                // Lägg till chunken, inklusive avgränsaren
                chunks.add(remainingText.substring(0, lastCleanBreak));

                // Uppdatera återstående text, trimma whitespace i början
                remainingText = remainingText.substring(lastCleanBreak).trim();

            } else {
                // 3. Fallback: Om ingen ren brytpunkt hittades i närheten, klipp bara av (vid char)
                // Det är bättre än att krascha, men kommer att resultera i brutna ord.
                chunks.add(remainingText.substring(0, cutoff));
                remainingText = remainingText.substring(cutoff).trim();
            }
        }

        // 4. Lägg till resten (den sista, lilla chunken)
        if (!remainingText.isEmpty()) {
            chunks.add(remainingText);
        }

        return chunks;
    }

    // Fallback: Kapar texten om den är en enda, jättelång sträng utan avgränsare
    private List<String> splitByCharacter(String text) {
        List<String> parts = new ArrayList<>();
        int currentPosition = 0;
        while (currentPosition < text.length()) {
            int endPosition = Math.min(text.length(), currentPosition + chunkSize);
            parts.add(text.substring(currentPosition, endPosition));
            currentPosition = endPosition;
        }
        return parts;
    }

    // ----------------------------------------------------------------------
    // --- Överlappningslogik (Lägger till kontext) ---
    // ----------------------------------------------------------------------

    private List<String> applyOverlap(List<String> rawChunks) {
        List<String> finalChunks = new ArrayList<>();
        String previousChunk = "";

        for (int i = 0; i < rawChunks.size(); i++) {
            String currentChunk = rawChunks.get(i);

            // 1. Bestäm överlappningen från föregående chunk
            String overlap = "";
            if (!previousChunk.isEmpty()) {
                // Beräkna hur lång överlappning vi ska ta (max chunkOverlap)
                int overlapLength = Math.min(chunkOverlap, previousChunk.length());
                // Hämta slutet av föregående chunk
                overlap = previousChunk.substring(previousChunk.length() - overlapLength);
            }

            // 2. Skapa den slutgiltiga chunken
            String finalChunk = overlap + currentChunk;

            // 3. Om chunken blev för lång på grund av överlappningen, trunkera från början.
            // Detta bibehåller den nyligen tillagda texten (currentChunk) i slutet.
            if (finalChunk.length() > chunkSize) {
                // Kapa bort de äldsta tecknen
                finalChunk = finalChunk.substring(finalChunk.length() - chunkSize);
            }

            finalChunks.add(finalChunk);

            // 4. Uppdatera föregående chunk för nästa iteration
            previousChunk = finalChunk;
        }

        return finalChunks;
    }

    // --- Enkel main-metod för att testa logiken ---
    /* public static void main(String[] args) {
        String testText = "Detta är första stycket.\n\nDetta är andra stycket som är lite längre. Vi vill säkerställa att överlappning fungerar. Här kommer en mening till. Här är en till. Här är den sista meningen i chunket.\n\nDetta är det tredje stycket som kommer att få överlappning från det andra stycket.";

        RecursiveTextSplitter splitter = new RecursiveTextSplitter(150, 30); // 150 tecken max, 30 tecken överlapp
        List<String> chunks = splitter.splitText(testText);

        System.out.println("--- Splitting Results ---");
        for (int i = 0; i < chunks.size(); i++) {
            System.out.printf("Chunk %d (Längd: %d):\n\"%s...\"\n",
                              i + 1, chunks.get(i).length(), chunks.get(i).substring(0, Math.min(chunks.get(i).length(), 100)));
        }
    }
    */
}