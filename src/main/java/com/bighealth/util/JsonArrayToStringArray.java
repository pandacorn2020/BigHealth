package com.bighealth.util;
import org.json.JSONArray;
import org.json.JSONObject;

public class JsonArrayToStringArray {
    public static String[] convert(String jsonContent) {

        try {
            // Convert JSON content to a JSONArray
            JSONArray jsonArray = new JSONArray(jsonContent);

            // Convert each element of the JSON array to a string array
            String[] stringArray = new String[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                Object element = jsonArray.get(i);

                // Check the type of the element and convert to string accordingly
                if (element instanceof JSONObject) {
                    stringArray[i] = ((JSONObject) element).toString();
                } else if (element instanceof JSONArray) {
                    stringArray[i] = ((JSONArray) element).toString();
                } else if (element instanceof String) {
                    stringArray[i] = (String) element;
                } else if (element instanceof Integer) {
                    stringArray[i] = Integer.toString((Integer) element);
                } else if (element instanceof Boolean) {
                    stringArray[i] = Boolean.toString((Boolean) element);
                } else if (element == JSONObject.NULL) {
                    stringArray[i] = "null";  // Handle JSON null values
                } else {
                    stringArray[i] = element.toString();  // Default fallback for other types
                }
            }
            return stringArray;
        } catch (Exception e) {
            return new String[] {jsonContent};
        }
    }
}
