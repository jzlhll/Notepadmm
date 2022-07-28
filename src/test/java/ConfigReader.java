import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

public class ConfigReader {
    private final HashMap<String, String> kvs = new HashMap<>();

    public void parse(String mFileName) throws IOException {
       List<String> ss = Files.readAllLines(Path.of(mFileName));
       for (var s : ss) {
           var st = s.trim();
           if (st.length() <= 2 || st.startsWith("#")) {
               continue;
           }

           String[] kv = s.split("=");

           String vt = kv[1].trim();
           //key="value"
           //key='value'
           //key= 'value'
           //key =  value
           if (vt.charAt(0) == '\'' && vt.charAt(vt.length() - 1) == '\'') {
               vt = vt.substring(1, vt.length() - 1);
               kvs.put(kv[0].trim(), (vt));
           } else if (vt.charAt(0) == '\"' && vt.charAt(vt.length() - 1) == '\"') {
               vt = vt.substring(1, vt.length() - 1);
               kvs.put(kv[0].trim(), (vt));
           } else {
               kvs.put(kv[0].trim(), trimLeft(kv[1]));
           }
       }
    }

    private static String trimLeft(String src) {
        var i = 0;
        while (src.charAt(i) == ' ') {
            i++;
        }

        return src.substring(i);
    }

    public String get(String k) {
        return kvs.get(k);
    }

    public boolean getBoolean(String k) {
        return Boolean.parseBoolean(kvs.get(k));
    }

    public boolean containsKey(String k) {
        return kvs.containsKey(k);
    }

    public int getInt(String k) {
        return Integer.parseInt(kvs.get(k));
    }

    public float getFloat(String k) {
        return Float.parseFloat(kvs.get(k));
    }
}
