/*
 *
 *    Copyright 2016 jshook
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * /
 */

package io.nosqlbench.engine.api.activityconfig;

import io.nosqlbench.engine.api.activityconfig.rawyaml.RawStmtsDocList;
import io.nosqlbench.engine.api.activityconfig.rawyaml.RawStmtsLoader;
import io.nosqlbench.engine.api.activityconfig.yaml.StmtsDocList;
import io.nosqlbench.engine.api.templating.StrInterpolator;
import io.nosqlbench.nb.api.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class StatementsLoader {

    private final static Logger logger = LoggerFactory.getLogger(StatementsLoader.class);

    public enum Loader {
        original,
        generified
    }

    public static StmtsDocList loadString(String yamlContent) {
        RawStmtsLoader loader = new RawStmtsLoader();
        loader.addTransformer(new StrInterpolator());
        RawStmtsDocList rawDocList = loader.loadString(logger, yamlContent);
        StmtsDocList layered = new StmtsDocList(rawDocList);
        return layered;
    }

    public static StmtsDocList loadContent(
            Logger logger,
            Content<?> content) {
        RawStmtsLoader loader = new RawStmtsLoader();
        loader.addTransformer(new StrInterpolator());
        RawStmtsDocList rawDocList = loader.loadString(logger, content.get().toString());
        StmtsDocList layered = new StmtsDocList(rawDocList);
        return layered;
    }

    public static StmtsDocList loadPath(
            Logger logger,
            String path,
            String... searchPaths) {
        RawStmtsDocList list = null;

        RawStmtsLoader gloaderImpl = new RawStmtsLoader();
        gloaderImpl.addTransformer(new StrInterpolator());

        list = gloaderImpl.loadPath(logger, path, searchPaths);
        return new StmtsDocList(list);
    }

    public static StmtsDocList loadPath(
            Logger logger,
            String path,
            Function<String, String> transformer,
            String... searchPaths) {
        RawStmtsDocList list = null;

        RawStmtsLoader gloaderImpl = new RawStmtsLoader();
        gloaderImpl.addTransformer(transformer);
        list = gloaderImpl.loadPath(logger, path, searchPaths);
        return new StmtsDocList(list);
    }

//    public static StmtsDocList load(Logger logger, String path, String... searchPaths) {
//        Content<?> content = NBIO.all()
//                .prefix(searchPaths)
//                .name(path)
//                .one();
//        try {
//            RawYamlStatementLoader loader = new RawYamlStatementLoader();
//            RawStmtsDocList rawDocList = loader.loadString(logger, content.get());
//            StmtsDocList layered = new StmtsDocList(rawDocList);
//            return layered;
//        } catch (Exception e) {
//            throw new RuntimeException("error while reading file " + path, e);
//        }
//    }

//    public static StmtsDocList load(Logger logger, String path, Function<String, String> transformer, String... searchPaths) {
//        RawYamlStatementLoader loader = new RawYamlStatementLoader(transformer);
//        RawStmtsDocList rawDocList = loader.load(logger, path, searchPaths);
//        StmtsDocList layered = new StmtsDocList(rawDocList);
//        return layered;
//    }


}
