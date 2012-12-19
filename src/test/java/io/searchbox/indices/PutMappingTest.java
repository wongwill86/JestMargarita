package io.searchbox.indices;

import java.util.Map;

import org.elasticsearch.common.settings.ImmutableSettings;
import org.junit.Test;

import static junit.framework.Assert.*;

/**
 * 
 * @author Will Wong
 *
 */
public class PutMappingTest {

    
    public String index = "twitter";
    public String type = "tweet";
    
    public String source = "{" + 
    		"    \"tweet\" : {" + 
    		"        \"properties\" : {" + 
    		"            \"message\" : {\"type\" : \"string\", \"store\" : \"yes\"}" + 
    		"        }" + 
    		"    }" + 
    		"}";
    public String uri = index + "/" + type;
    
    @Test
    public void createPutMappingWithoutSettings() {
        PutMapping putMapping = new PutMapping.Builder(source).index(index).type(type).build();
        assertEquals(uri, putMapping.getURI());
        assertEquals("PUT", putMapping.getRestMethodName());
        assertEquals("PUT_MAPPING", putMapping.getName());
        assertTrue(source.equals(putMapping.getData()));
    }

}
