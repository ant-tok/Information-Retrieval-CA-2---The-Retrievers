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


public class parsing_topics {


    public static void main(String[] args) {

        ArrayList<Document> docs = parse(args[0]);
        System.out.println("Total topics: " + docs.size());
    }

    
    public static ArrayList<Document> parse(String path) {
        ArrayList<Document> topics = new ArrayList<>();
        Path root_of_topics = Paths.get(path);


        readFile(root_of_topics, topics);

        System.out.println("Parsed " + topics.size() + " topics.");
        return topics;
    }

    
    private static void readFile(Path file, List<Document> topic_list) {
        // use ISO-8859-1 to read the file cuz some files may contain non-UTF-8 characters
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String line;
            StringBuilder curr = null;

            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();

                //Start new document at <top>
                if (trimmed.startsWith("<top>")) {
                    curr = new StringBuilder();
                } 

                // Save document at </top>
                else if (trimmed.startsWith("</top>")) {
                    if (curr != null) {
                        Document d = buildDocument(curr.toString());
                        if (d != null) {
                            topic_list.add(d);
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
               
        String numField = Found_Field(Raw_Text, new String[] {"<num>", "<title>"});
        String titleField = Found_Field(Raw_Text, new String[] {"<title>", "<desc>"});
        String descField = Found_Field(Raw_Text, new String[] {"<desc>", "<narr>"});
        String narrField = Found_Field(Raw_Text, new String[] {"<narr>", "</top>"});

        // Get number from line
        String topicId = numField.replace("Number:", "").trim();

        if (topicId.isEmpty()) {
            return null;
        }

        // Remove unneeded words
        if (descField.toLowerCase().startsWith("description:")) {
            descField = descField.substring("description:".length()).trim();
        }
        if (narrField.toLowerCase().startsWith("narrative:")) {
            narrField = narrField.substring("narrative:".length()).trim();
        }

        Document doc = new Document();
        doc.add(new StringField("id", topicId, Field.Store.YES));
        
        if (!titleField.isEmpty()) {
            doc.add(new TextField("title", titleField, Field.Store.YES));
        }
        if (!descField.isEmpty()) {
            doc.add(new TextField("description", descField, Field.Store.YES));
        }
        if (!narrField.isEmpty()) {
            doc.add(new TextField("narrative", narrField, Field.Store.YES));
        }
        
        return doc;
    }


    private static String Found_Field(String s, String[] pair) {


            int start = s.indexOf(pair[0]);
            if (start == -1) {
                return "";
            }
            start += pair[0].length();

            int end = s.indexOf(pair[1], start);
            if (end == -1) {
                return "";
            }

            String result = s.substring(start, end).trim();
            if (!result.isEmpty()) {
                return result;
            }
            return "";
        
        
    }

}
