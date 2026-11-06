package tokaja;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;


public class parsing {

    // Tags to try, in order, for each logical field. First non-empty hit wins
    // Diff tags for diff sources so many must be tried
    private static final String[][] TITLE_TAGS = {
        {"<HEADLINE>", "</HEADLINE>"}, {"<TI>", "</TI>"}, {"<DOCTITLE>", "</DOCTITLE>"}};
        // FT, LA Times            FBIS                 FR94
    
    private static final String[][] DATE_TAGS = {
        {"<DATE1>", "</DATE1>"}, {"<DATE>", "</DATE>"}}; 
        // FBIS                    the rest
    
    private static final String[][] AUTHOR_TAGS = {
        {"<BYLINE>", "</BYLINE>"}, {"<AU>", "</AU>"}, {"<SIGNER>", "</SIGNER>"}};
        // FT, LA Times            FBIS                 FR94

    // Only for fr94
    private static final String[][] SUMMARY_PATTERN = {
        {"<SUMMARY>", "</SUMMARY>"}};
    private static final String[][] SUPPLEM_PATTERN = {
        {"<SUPPLEM>", "</SUPPLEM>"}};


    public static void main(String[] args) {

        ArrayList<Document> docs = parse(args[0]);
        System.out.println("Total documents: " + docs.size());
    }

    
    public static ArrayList<Document> parse(String path) {
        ArrayList<Document> docs = new ArrayList<>();
        Path root_of_docs = Paths.get(path);

        try {
            // Parsing through every file in a directory
            if (Files.isDirectory(root_of_docs)) {
                List<Path> files = new ArrayList<>();
                try (Stream<Path> walk = Files.walk(root_of_docs)) {
                    walk.filter(Files::isRegularFile).filter(parsing::checkIfDocument).forEach(files::add);
                }
                for (Path f : files) {
                    readFile(f, docs);
                }
            } else { // If only 1 file, only parse taht
                readFile(root_of_docs, docs);
            }
        } catch (IOException e) {
            System.err.println("Error at " + path + ": " + e.getMessage());
        }

        System.out.println("Parsed " + docs.size() + " documents.");
        return docs;
    }

    
    // Skip files that arent docs, namely readmes and DS_store files.
    private static boolean checkIfDocument(Path path_to_doc) {
        String name = path_to_doc.getFileName().toString().toLowerCase();
        if (name.startsWith("read") || name.startsWith(".DS_Store")){
             return false;
        }

        // Skip the dtds folder 
        for (Path part : path_to_doc) {
            if (part.toString().equalsIgnoreCase("dtds")) {
                return false;
            }
        }
        return true;
    }

    
    private static void readFile(Path file, List<Document> docs) {
        // use ISO-8859-1 to read the file cuz some files may contain non-UTF-8 characters
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String line;
            StringBuilder curr = null;

            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();

                //Start new document at <DOC>
                if (trimmed.startsWith("<DOC>")) {
                    curr = new StringBuilder();
                } 

                // Save document at </DOC>
                else if (trimmed.startsWith("</DOC>")) {
                    if (curr != null) {
                        Document d = buildDocument(curr.toString());
                        if (d != null) {
                            docs.add(d);
                        }   
                        curr = null;
                    }
                } 
                else if (curr != null) {
                    curr.append(line).append('\n');
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading " + file + ": " + e.getMessage());
        }
    }

    // Construct lucene document from the Raw_Text text of a doc
    private static Document buildDocument(String Raw_Text) {
        
        int start_pos = Raw_Text.indexOf("<DOCNO>", 0) + "<DOCNO>".length();
        int end_pos = Raw_Text.indexOf("</DOCNO>", start_pos);

        String curr_doc = Raw_Text.substring(start_pos, end_pos);

        if (curr_doc == null){
            return null;
        }
        curr_doc = curr_doc.trim();
        if (curr_doc.isEmpty()) {
            return null;
        }

        String title = Found_Field(Raw_Text, TITLE_TAGS);
        String date = Found_Field(Raw_Text, DATE_TAGS);
        String author = Found_Field(Raw_Text, AUTHOR_TAGS);
        String summary = Found_Field(Raw_Text, SUMMARY_PATTERN);
        String supplem = Found_Field(Raw_Text, SUPPLEM_PATTERN);

        
        // Body is everythign between <TEXT> tags
        StringBuilder body = new StringBuilder();
        int pos = 0;
        while (true) {
            int start = Raw_Text.indexOf("<TEXT>", pos);
            if (start == -1){
                 break;
            }
            start += "<TEXT>".length();
            int end = Raw_Text.indexOf("</TEXT>", start);

            if (end == -1){
                end = Raw_Text.length(); 
            }
            body.append(Raw_Text, start, end).append(' ');
            
            pos = end;
        }

        Document doc = new Document();
        doc.add(new StringField("id", (curr_doc), Field.Store.YES));
        if (!title.isEmpty()) {
            doc.add(new TextField("title", (title), Field.Store.YES));
        }
        if (!author.isEmpty()) {
            doc.add(new TextField("author", (author), Field.Store.YES));
        }
        if (!date.isEmpty()) {
            doc.add(new TextField("date", (date), Field.Store.YES));
        }
        if (!body.toString().isEmpty()) {
            doc.add(new TextField("body", (body.toString()), Field.Store.YES));
        }
        if (!summary.isEmpty()) {
            doc.add(new TextField("summary", (summary), Field.Store.YES));
        }
        if (!supplem.isEmpty()) {
            doc.add(new TextField("supplemental", (supplem), Field.Store.YES));
        }
        return doc;
    }


    private static String Found_Field(String s, String[][] tagPairs) {
        for (String[] pair : tagPairs) {

            int start = s.indexOf(pair[0]);
            if (start == -1) {
                continue;
            }
            start += pair[0].length();

            int end = s.indexOf(pair[1], start);
            if (end == -1) {
                continue;
            }

            String result = s.substring(start, end).trim();
            if (!result.isEmpty()) {
                return result;
            }
        }
        return "";
    }

}
