/*
Copyright 2018 axpendix@hotmail.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.tcgone.carddb.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.tcgone.carddb.model.Card;
import net.tcgone.carddb.model.Move;
import net.tcgone.carddb.model.SetFile;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.representer.Representer;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class SetWriter {

    private Yaml yaml;
    private ObjectMapper objectMapper;

    @PostConstruct
    private void init() {
        DumperOptions options = new DumperOptions();
        options.setAllowUnicode(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.AUTO);

        CustomPropertyUtils customPropertyUtils = new CustomPropertyUtils();
        Representer customRepresenter = new Representer() {
            @Override
            protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
                // if value of property is null, ignore it.
                if (propertyValue == null) {
                    return null;
                } else {
                    return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
                }
            }
            protected MappingNode representJavaBean(java.util.Set<Property> properties, Object javaBean) {
                List<NodeTuple> value = new ArrayList<NodeTuple>(properties.size());
                Tag tag;
                Tag customTag = classTags.get(javaBean.getClass());
                tag = customTag != null ? customTag : new Tag(javaBean.getClass());
                // flow style will be chosen by BaseRepresenter
                MappingNode node = new MappingNode(tag, value, DumperOptions.FlowStyle.AUTO);
                representedObjects.put(javaBean, node);
                DumperOptions.FlowStyle bestStyle = DumperOptions.FlowStyle.BLOCK;
                for (Property property : properties) {
                    Object memberValue = property.get(javaBean);
                    Tag customPropertyTag = memberValue == null ? null
                            : classTags.get(memberValue.getClass());
                    NodeTuple tuple = representJavaBeanProperty(javaBean, property, memberValue,
                            customPropertyTag);
                    if (tuple == null) {
                        continue;
                    }
                    if (!((ScalarNode) tuple.getKeyNode()).isPlain()) {
                        bestStyle = DumperOptions.FlowStyle.BLOCK;
                    }
                    Node nodeValue = tuple.getValueNode();
                    if (!(nodeValue instanceof ScalarNode && ((ScalarNode) nodeValue).isPlain())) {
                        bestStyle = DumperOptions.FlowStyle.BLOCK;
                    }
                    if("abilities".equals(((ScalarNode) tuple.getKeyNode()).getValue())){
                        bestStyle = DumperOptions.FlowStyle.BLOCK;
                    }
                    value.add(tuple);
                }
                if (defaultFlowStyle != DumperOptions.FlowStyle.AUTO) {
                    node.setFlowStyle(defaultFlowStyle);
                } else {
                    node.setFlowStyle(bestStyle);
                }
                return node;
            }

            @Override
            protected Node representMapping(Tag tag, Map<?, ?> mapping, DumperOptions.FlowStyle flowStyle) {
                List<NodeTuple> value = new ArrayList<NodeTuple>(mapping.size());
                MappingNode node = new MappingNode(tag, value, flowStyle);
                representedObjects.put(objectToRepresent, node);
                DumperOptions.FlowStyle bestStyle = DumperOptions.FlowStyle.FLOW;
                for (Map.Entry<?, ?> entry : mapping.entrySet()) {
                    Node nodeKey = representData(entry.getKey());
                    Node nodeValue = representData(entry.getValue());
                    if (!(nodeKey instanceof ScalarNode && ((ScalarNode) nodeKey).isPlain())) {
                        bestStyle = DumperOptions.FlowStyle.BLOCK;
                    }
                    if (!(nodeValue instanceof ScalarNode && ((ScalarNode) nodeValue).isPlain())) {
                        bestStyle = DumperOptions.FlowStyle.BLOCK;
                    }
                    value.add(new NodeTuple(nodeKey, nodeValue));
                }
                if (flowStyle == DumperOptions.FlowStyle.AUTO) {
                    if (defaultFlowStyle != DumperOptions.FlowStyle.AUTO) {
                        node.setFlowStyle(defaultFlowStyle);
                    } else {
                        node.setFlowStyle(bestStyle);
                    }
                }
                return node;
            }
        };
        customRepresenter.setPropertyUtils(customPropertyUtils);
        yaml = new Yaml(customRepresenter, options);

//        objectMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));

    }

    private void write(SetFile setFile, String filename) throws IOException {
        //        objectMapper.writeValue(new File(filename),setFile);
        for (Card card : setFile.cards) {
            card.set=null;
            card.merged=null;
            if(card.moves!=null){
                for (Move move : card.moves) {
                    if(move.damage!=null && move.damage.isEmpty()) {
                        move.damage = null;
                    }
                    if(move.text!=null && move.text.isEmpty()){
                        move.text = null;
                    }
                    if(move.cost!=null && move.cost.size()==1 && move.cost.get(0)==null){
                        move.cost=new ArrayList<>();
                    }
                }
            }
        }
        String dump = yaml.dumpAs(setFile, Tag.MAP, null);
        BufferedWriter out = new BufferedWriter
                (new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8));
        out.write(dump);
        out.close();
    }
    public void writeAll(List<Card> cards) throws IOException {
        cards.sort((o1, o2) -> {
            try {
                Integer n1 = Integer.parseInt(o1.number);
                Integer n2 = Integer.parseInt(o2.number);
                return n1.compareTo(n2);
            } catch (NumberFormatException e) {
                return o1.number.compareTo(o2.number);
            }
        });
        Map<String, SetFile> setFileMap=new HashMap<>();
        for (Card card : cards) {
            String key = card.set.enumId;
            if(!setFileMap.containsKey(key)){
                SetFile setFile = new SetFile();
                setFile.set=card.set;
                setFile.cards=new ArrayList<>();
                setFile.cards.add(card);
                setFileMap.put(key, setFile);
            } else {
                setFileMap.get(key).cards.add(card);
            }
        }
        new File("output").mkdirs();
        for (SetFile setFile : setFileMap.values()) {
            String filename = String.format("output/%s-%s.yaml", setFile.set.id, setFile.set.enumId.toLowerCase(Locale.ENGLISH));
            this.write(setFile, filename);
        }
    }
}