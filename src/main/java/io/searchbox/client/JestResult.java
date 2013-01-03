package io.searchbox.client;

import io.searchbox.annotations.JestId;

import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;

/**
 * @author Dogukan Sonmez
 */


public class JestResult {

    final static Logger log = LoggerFactory.getLogger(JestResult.class);
    public static final String ES_METADATA_ID = "es_metadata_id";

    private Map jsonMap;

    private String jsonString;

    private String pathToResult;

    private boolean isSucceeded;

    public String getPathToResult() {
        return pathToResult;
    }

    public void setPathToResult(String pathToResult) {
        this.pathToResult = pathToResult;
    }

    public Object getValue(String key) {
        return jsonMap.get(key);
    }

    public boolean isSucceeded() {
        return isSucceeded;
    }

    public void setSucceeded(boolean succeeded) {
        isSucceeded = succeeded;
    }

    public String getJsonString() {
        return jsonString;
    }

    public void setJsonString(String jsonString) {
        this.jsonString = jsonString;
    }

    public String getErrorMessage() {
        return (String) jsonMap.get("error");
    }

    public Map getJsonMap() {
        return jsonMap;
    }

    public void setJsonMap(Map jsonMap) {
        this.jsonMap = jsonMap;
    }

    public <T> T getSourceAsObject(Class<?> clazz) {
        List sourceList = ((List) extractSource());
        if (sourceList.size() > 0)
            return createSourceObject(sourceList.get(0), clazz);
        else
            return null;
    }

    private <T> T createSourceObject(Object source, Class<?> type) {
        Object obj = null;
        try {
            if (source instanceof Map) {
                Gson gson = new Gson();
                String json = gson.toJson(source, Map.class);
                obj = gson.fromJson(json, type);
            } else {
                obj = type.cast(source);
            }

            //Check if JestId is visible
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(JestId.class)) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(obj);
                        if (value == null) {
                            field.set(obj, ((Map) source).get(ES_METADATA_ID));
                        }
                    } catch (IllegalAccessException e) {
                        log.error("Unhandled exception occurred while getting annotated id from source");
                    }
                    break;
                }
            }

        } catch (Exception e) {
            log.error("Unhandled exception occurred while converting source to the object ." + type.getCanonicalName(), e);
        }
        return (T) obj;
    }

    public <T> T getSourceAsObjectList(Class<?> type) {
        List<Object> objectList = new ArrayList<Object>();
        if (!isSucceeded) return (T) objectList;
        List<Object> sourceList = (List<Object>) extractSource();
        for (Object source : sourceList) {
            Object obj = createSourceObject(source, type);
            if (obj != null) objectList.add(obj);
        }
        return (T) objectList;
    }

    public Object extractSource() {
        return extractSourceFromPath(getKeys());
    }

    /**
     * Drill down through the jsonMapObject via the path given ( delimited by / ).
     * 
     * 
     * Note: the last token from the delimited path will be used to traverse the final object or objects found
     *      i.e. hits/hits/_source will drill down the path until second to last node ( hits/hits ) 
     *          and analyze the next drill down object
     *          if the next drildown object is a Map, it will try to access the map's value for "_source"
     *          if the next drilldown object is a List, it will try to traverse every object within that list
     *              for the value of "_source" ( if the object is not a map, then we do not add it to our results)
     *              
     *      Logic refactored from extractSource()
     *      
     * @param path
     * @return a list of the objects that we have found
     */
    public List<Object> extractSourceFromPath(String[] keys){
        List<Object> sourceList = new ArrayList<Object>();
        if(jsonMap == null){
            return sourceList;
        }
        if(keys == null){
            sourceList.add(jsonMap);
            return sourceList;
        }
        
        Object node = jsonMap;
        try{
            // drill down to 2nd to last node in path
            for(int keyIndex = 0; keyIndex <= keys.length - 2; keyIndex++){
                node = ((Map<String,Object>)node).get(keys[keyIndex]);
                if(node == null){
                    throw new InvalidObjectException(keys[keyIndex] + " not found");
                }
            }
            
            String lastKey = keys[keys.length - 1];
            
            if(node instanceof Map){
                Object endChild = ((Map<String,Object>) node).get(lastKey);
                if(endChild != null){
                    sourceList.add(endChild);
                } else {
                    throw new InvalidObjectException(lastKey + " not found");
                }
            }
            
            if(node instanceof List){
                for (Object endChild : (List) node) {
                    if (endChild instanceof Map) {
                        Map<String, Object> source = (Map<String, Object>) ((Map<String,Object>) endChild).get(lastKey);
                        if (source != null) {
                            source.put(ES_METADATA_ID, ((Map) endChild).get("_id"));
                            sourceList.add(source);
                        }
                    }
                }
            }
        } catch(InvalidObjectException e){
            log.warn("The path <" + keys + "> could not be resolved from the jsonObject", e);
        }
        return sourceList;
    }
    protected String[] getKeys() {
        return pathToResult == null ? null : (pathToResult + "").split("/");
    }

    public <T> List<T> getFacets(Class<?> type) {
        List<T> facets = new ArrayList<T>();
        if (jsonMap != null) {
            Constructor c;
            try {
                Map<String, Map> facetsMap = (Map<String, Map>) jsonMap.get("facets");
                for (Object facetKey : facetsMap.keySet()) {
                    Map facet = facetsMap.get(facetKey);
                    if (facet.get("_type").toString().equalsIgnoreCase(type.getField("TYPE").get(null).toString())) {
                        c = Class.forName(type.getName()).getConstructor(String.class, Map.class);
                        facets.add((T) c.newInstance(facetKey.toString(), facetsMap.get(facetKey)));
                    }
                }
                return facets;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return facets;
    }
}
