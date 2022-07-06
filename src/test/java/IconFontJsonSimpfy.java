import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class IconFontJsonSimpfy {
    /*
    格式如下：

    {
  "id": "1813952",
  "name": "totalAU",
  "font_family": "iconfont",
  "css_prefix_text": "icon-",
  "description": "",
  "glyphs": [
    {
      "icon_id": "577367",
      "name": "搜索类目",
      "font_class": "sousuoleimu",
      "unicode": "e765",
      "unicode_decimal": 59237
    },
    .....
      ]
}
    end。。。。
     */

    //private static final String file = "/Users/allan/Documents/codes/MyCod/ATools/src/main/resources/font/iconfont.ttf";
    private static final String file = "/Users/allan/Downloads/font_1813952_k88nyrilopk/iconfont.json";

    private static void simplify() throws IOException {
        Gson gson = new Gson();
        String str = Files.readString(Path.of(file));
        var jsonObj = gson.fromJson(str, JsonObject.class);
        var glyphs = jsonObj.get("glyphs");

        var list = new ArrayList<Item>();

        if (glyphs instanceof JsonArray) {
            JsonArray  glyphsArr = (JsonArray) glyphs;
            for (com.google.gson.JsonElement jsonElement : glyphsArr) {
                JsonObject jo = (JsonObject) jsonElement;
                Item item = new Item();
                item.name = jo.get("font_class").getAsString();
                item.unicode = "\\u" + jo.get("unicode").getAsString();
                list.add(item);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (var item : list) {
            sb.append(item).append("\n");
        }
        System.out.println(sb);
    }

    public static void main(String[] args) throws IOException {
       simplify();
       String s = null;
    }

    public static class Item {
        public String name;
        public String unicode;

        @Override
        public String toString() {
            return name + ":" + unicode;
        }
    }
}
