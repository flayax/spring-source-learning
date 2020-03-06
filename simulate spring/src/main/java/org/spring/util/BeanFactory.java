package org.spring.util;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class BeanFactory {
    Map<String, Object> map = new HashMap<String, Object>();

    public BeanFactory(String xml) {
        parseXml(xml);
    }

    /**
     * 解析XML配置文件
     *
     * @param xml
     */
    public void parseXml(String xml) {
        // 生成xml文件对象
        // 获取xml文件路径
        String path = this.getClass().getResource("/").getPath() + "//" + xml;
        try {
            // 解决路径中文乱码问题
            path = URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        File file = new File(path);
        SAXReader reader = new SAXReader();
        try {
            // 生成xml的DOM树
            Document document = reader.read(file);
            // 获得DOM树上的根节点
            Element elementRoot = document.getRootElement();

            // 从根节点开始向下遍历树上的所有节点
            for (Iterator<Element> it = elementRoot.elementIterator(); it.hasNext(); ) {
                /**
                 * 1、实例化对象
                 */

                // 获取一个标签
                Element elementFirstChil = it.next();

                // 获取这个标签的id属性对象
                Attribute attributeId = elementFirstChil.attribute("id");
                // 获取id属性的值
                String beanName = attributeId.getValue();

                // 获取这个标签的class属性对象
                Attribute attributeClass = elementFirstChil.attribute("class");
                // 获取class属性的值
                String clazzName = attributeClass.getValue();
                // 获取bean的class对象
                Class clazz = Class.forName(clazzName);

                // 生成对应bean的对象引用
                Object object = null;


                /**
                 * 2、维护依赖关系
                 * 看这个对象有没有依赖（判断xml的bean标签中是否有property这个子标签/或者判断类是否有属性）
                 * 如果有则注入
                 */
                for (Iterator<Element> itSecond = elementFirstChil.elementIterator(); itSecond.hasNext(); ) {
                    /**
                     * 得到ref的value,通过value得到要注入的对象（从map中取得）
                     * 得到name的值，根据这个值回去一个Filed对象（Filed对象用来取得一个类中的属性）   这里为了简单默认使用set方法来进行注入，所以要取name值
                     * 通过filed的set方法来注入对象
                     */
                    // 取得二级子标签很替
                    Element elementSecondChil = itSecond.next();
                    // 查看<bean>标签的子标签中是否有property标签，有的话说明这个bean有依赖对象，需要用setter方法注入
                    if (elementSecondChil != null && "property".equals(elementSecondChil.getName())) {
                        // 生成对应bean的对象实例
                        object = clazz.newInstance();

                        // 获取依赖对象的id
                        String refValue = elementSecondChil.attribute("ref").getValue();
                        // 从map中获取要注入的bean
                        Object injetObject = map.get(refValue);

                        // 获取<property>标签的name属性，表示的是要匹配的set方法名，但是这里为了简单起见，就默认service对象dao的set方法名与service对象中UserDao属性名一致,都是dao
                        String nameValue = elementSecondChil.attribute("name").getValue();
                        // 获取该类属性名为nameValue的属性的Field对象，来进行注入
                        Field field = clazz.getDeclaredField(nameValue);
                        // 因为这个属性是私有的，所以要开启暴力反射，将Accessible设置为true
                        field.setAccessible(true);
                        // 将要注入的对象注入到object中，这里object表示的是被注入的对象，也就是service，injetObject表示的是注入的对象，也就是dao
                        field.set(object, injetObject);


                    // 使用构造方法注入
                    } else if (elementSecondChil != null && "constructor-arg".equals(elementSecondChil.getName())){
                        // 获取依赖对象的id
                        String refValue = elementSecondChil.attribute("ref").getValue();

                        // 从map中获取要注入的bean
                        Object injetObject = map.get(refValue);

                        // 获取被注入对象的属性名
                        Attribute nameAttribute = elementSecondChil.attribute("name");
                        String nameValue = null;
                        if (nameAttribute != null) {
                            nameValue = nameAttribute.getValue();
                        }

                        // 如果XML标签传入了name属性就直接通过注入对象的Field类获取注入对象的Class
                        if (nameValue != null) {
                            // 获取该类属性名为nameValue的属性的Field对象
                            Field field = clazz.getDeclaredField(nameValue);
                            // 直接通过Field对象获取注入对象的Class
                            Class injetObjectClazz = field.getType();
                            // 通过被注入对象的Class获取构造方法对象，并将注入对象的Class传入，这样就可以用这个参数是注入对象的构造方法对象来创建注入dao的service对象
                            Constructor constructor = clazz.getConstructor(injetObjectClazz);
                            // 将注入对象dao传入构造方法中并且生成service的实例对象
                            object = constructor.newInstance(injetObject);


                        // 如果XML标签没有传入name属性就通过ref属性指定的在spring容器中的bean获取注入对象的Class
                        } else {
                            // 通过spring容器中的bean获取注入对象的Class
                            Class injetObjectClazz = injetObject.getClass();
                            // 由于在spring容器中的bean的类型是UserDaoImpl，而被注入对象中属性的类型是接口UserDao，所以会出现两者类型不一致，无法获取正确的构造方法的情况（构造方法参数类型是UserDao）
                            Constructor constructor = clazz.getConstructor(injetObjectClazz.getInterfaces()[0]);
                            // 将注入对象dao传入构造方法中并且生成service的实例对象
                            object = constructor.newInstance(injetObject);
                        }

                    }
                }

                // 没有子标签的情况需要单独给bean实例化
                if (object == null) {
                    object = clazz.newInstance();

                }

                // 将生成的bean实例对象存入到map中
                map.put(beanName, object);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(map);
    }

    public Object getBean(String beanName) {
        return map.get(beanName);
    }
}
