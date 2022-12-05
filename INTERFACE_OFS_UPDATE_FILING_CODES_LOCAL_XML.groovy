/*
*  @input: _codesDirectory - Class:String - \\\\torreypines\\OFS\\out\\codes\\fresnocr
*  @input: _lookuplist - Class:String - 
*  @input: _casecategory - Class:String - 
*  @input: _casetypeid - Class:String - 
*  @input: _filingcodeid - Class:String - 
*  @input: _name - Class:String - 
*  @input: _countrycode - Class:String - 
*  @output: _code - Class:String - 
*/
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.StringBuilder;

File codesDirectory = new File(_codesDirectory);
Map<String, String> map = new HashMap();
map.put("CASE_TYPE","casetypecodes.xml");
map.put("ODYSSEY_CASE_PARTICIPANT_ROLE","partytypecodes.xml");
map.put("ODYSSEY_IDENTIFICATION_SOURCE_TEXT","crossreferencecodes.xml");
map.put("PARTY_SUBMIT_TYPE","filingcodes.xml");
map.put("ODYSSEY_BINARY_CATEGORY_TEXT","filingcomponentcodes.xml");
map.put("ODYSSEY_BINARY_FORMAT_STANDARD","documenttypecodes.xml");
map.put("US_STATE","statecodes.xml");
map.put("COUNTRY","countrycodes.xml");
map.put("ADDRESS_COUNTRY","countrycodes.xml");
map.put("ETHNICITY","ethnicitycodes.xml");
map.put("RACE","racecodes.xml");
map.put("EYE_COLOR","eyecolorcodes.xml");
map.put("HAIR_COLOR","haircolorcodes.xml");
map.put("NAME_SUFFIX","namesuffixcodes.xml");
map.put("LANGUAGE","languagecodes.xml"); //complete
map.put("IDENTIFICATION_TYPE","driverlicensetypecodes.xml");
map.put("ODYSSEY_PHASE_TYPE_TEXT","chargephasecodes.xml");
map.put("STATUTE_SUBCATEGORY","degreecodes.xml");
map.put("STATUTE","statutecodes.xml");
//logger.debug("31: ${codesDirectory.listFiles().findAll({file -> file.getName() == map.get(_lookuplist)})}")
for (xmlCodeFile in codesDirectory.listFiles().findAll({file -> file.getName() == map.get(_lookuplist)})){
  logger.debug("xmlCodeFile: ${xmlCodeFile.getAbsolutePath()}");
  StringBuilder sb = new StringBuilder();
  BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(xmlCodeFile), "UTF-8"));
  try{
    for (line in bf.lines().toArray()){
      sb.append(line);
    }
    xmlObject = new XmlSlurper().parseText(sb.toString());
    if (_lookuplist == "CASE_TYPE"){
      xmlRow = xmlObject.SimpleCodeList.Row.find({r -> r.Value.find{ v -> v.@ColumnRef == 'name' && v.SimpleValue == _name} && r.Value.find{ v -> v.@ColumnRef == 'casecategory' && v.SimpleValue == _casecategory}  && r.Value.find{ v -> v.@ColumnRef == 'initial' && v.SimpleValue == "True"}});
    }else if(_lookuplist == "ODYSSEY_CASE_PARTICIPANT_ROLE"){
      xmlRow = xmlObject.SimpleCodeList.Row.find({r -> r.Value.find{ v -> v.@ColumnRef == 'name' && v.SimpleValue == _name} && r.Value.find{ v -> v.@ColumnRef == 'casetypeid' && v.SimpleValue == _casetypeid}  && r.Value.find{ v -> v.@ColumnRef == 'isavailablefornewparties' && v.SimpleValue == "True"}});
    } else if(_lookuplist == "ODYSSEY_IDENTIFICATION_SOURCE_TEXT"){
       xmlRow = xmlObject.SimpleCodeList.Row.find({r -> r.Value.find{ v -> v.@ColumnRef == 'name' && v.SimpleValue == _name} && r.Value.find{ v -> v.@ColumnRef == 'casetypeid'}});
    } else if(_lookuplist == "PARTY_SUBMIT_TYPE"){
      xmlRow = xmlObject.SimpleCodeList.Row.find({r -> r.Value.find{ v -> v.@ColumnRef == 'name' && v.SimpleValue == _name} && r.Value.find{ v -> v.@ColumnRef == 'casecategory' && v.SimpleValue == _casecategory}  && r.Value.find{ v -> v.@ColumnRef == 'filingtype' && (v.SimpleValue == "Subsequent" || v.SimpleValue == "Both" || v.SimpleValue == "Initial")}});
    } else if(_lookuplist == "ODYSSEY_BINARY_CATEGORY_TEXT"){
      xmlRow = xmlObject.SimpleCodeList.Row.find({r -> r.Value.find{ v -> v.@ColumnRef == 'filingcodeid' && v.SimpleValue == _filingcodeid}});
    } else if(_lookuplist == "ODYSSEY_BINARY_FORMAT_STANDARD"){
      xmlRow = xmlObject.SimpleCodeList.Row.find({r -> r.Value.find{ v -> v.@ColumnRef == 'filingcodeid' && v.SimpleValue == _filingcodeid} && r.Value.find{ v -> v.@ColumnRef == 'iscourtuseonly' && v.SimpleValue == "False"}});
    } else if(_lookuplist == "US_STATE"){
      xmlRow = xmlObject.SimpleCodeList.Row.find({r -> r.Value.find{ v -> v.@ColumnRef == 'name' && v.SimpleValue == _name} && r.Value.find{ v -> v.@ColumnRef == 'countrycode' && v.SimpleValue == _countrycode}});
    } else if(_lookuplist == "STATUTE"){
      logger.debug("57");
      String statuteName = _name.split(":")[0];
      String statuteWord = _name.split(":")[1];
      xmlRow = xmlObject.SimpleCodeList.Row.find({r -> r.Value.find{ v -> v.@ColumnRef == 'name' && v.SimpleValue == statuteName}});
      xmlRow = xmlRow.children().isEmpty() ? xmlObject.SimpleCodeList.Row.find({r -> r.Value.find{ v -> v.@ColumnRef == 'word' && v.SimpleValue == statuteWord}}) : xmlRow;
    } else {
      xmlRow = xmlObject.SimpleCodeList.Row.find({r -> r.Value.find{ v -> v.@ColumnRef == 'name' && v.SimpleValue == _name}});
    }
    _code = xmlRow.children().getAt(0).text();
  }catch(Exception ex){
    logger.debug("exception: ${ex.getMessage()}");
    bf.close();
  }finally{
    bf.close();
  }
}



