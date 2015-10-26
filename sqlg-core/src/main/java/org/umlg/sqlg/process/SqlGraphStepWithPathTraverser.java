package org.umlg.sqlg.process;

import com.google.common.collect.Multimap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tinkerpop.gremlin.process.traversal.Path;
import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ImmutablePath;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.B_O_P_S_SE_SL_Traverser;
import org.umlg.sqlg.strategy.BaseSqlgStrategy;

import java.util.*;

/**
 * Created by pieter on 2015/07/20.
 */
public class SqlGraphStepWithPathTraverser<T> extends B_O_P_S_SE_SL_Traverser<T> implements SqlgLabelledPathTraverser {

    private List<Pair<Path, Pair<Object, Optional<Long>>>> toEmit = new ArrayList<>();

    public SqlGraphStepWithPathTraverser(final T t, Multimap<String, Pair<Object, Optional<Long>>> labeledObjects, final Step<T, ?> step, final long initialBulk) {
        super(t, step, initialBulk);
        if (labeledObjects != null && !labeledObjects.isEmpty()) {
            Path localPath = ImmutablePath.make();
            customSplit(t, localPath, labeledObjects);
        }
    }

    public SqlGraphStepWithPathTraverser(final T t, final Step<T, ?> step, final long initialBulk) {
        super(t, step, initialBulk);
    }

    public List<Pair<Path, Pair<Object, Optional<Long>>>> getToEmit() {
        return this.toEmit;
    }

    /**
     * This odd logic is to ensure the path represents the path from left to right.
     * Calling this.path.extends(...) reverses the path. The test still pass but it seems wrong.
     */
    public void customSplit(final T t, Path currentPath, Multimap<String, Pair<Object, Optional<Long>>> labeledObjects) {
        boolean addT = true;
        List<String> sortedKeys = new ArrayList<>(labeledObjects.keySet());
        Collections.sort(sortedKeys);
        //This is to prevent duplicates in the path. Each labeled object will be present in the sql result set.
        //If the same object has multiple labels it will be present many times in the sql result set.
        //The  allLabeledElementsAsSet undoes this duplication by ensuring that there is only one path for the object with multiple labels.
        Map<String, Set<Object>> allLabeledElementMap = new HashMap<>();
        for (String label : sortedKeys) {
            Collection<Pair<Object, Optional<Long>>> labeledElements = labeledObjects.get(label);
            String realLabel;
            String pathLabel;
            if (label.contains(BaseSqlgStrategy.PATH_LABEL_SUFFIX)) {
                realLabel = label.substring(label.indexOf(BaseSqlgStrategy.PATH_LABEL_SUFFIX) + BaseSqlgStrategy.PATH_LABEL_SUFFIX.length());
                pathLabel = label.substring(0, label.indexOf(BaseSqlgStrategy.PATH_LABEL_SUFFIX) + BaseSqlgStrategy.PATH_LABEL_SUFFIX.length());
            } else if (label.contains(BaseSqlgStrategy.EMIT_LABEL_SUFFIX)) {
                realLabel = label.substring(label.indexOf(BaseSqlgStrategy.EMIT_LABEL_SUFFIX) + BaseSqlgStrategy.EMIT_LABEL_SUFFIX.length());
                pathLabel = label.substring(0, label.indexOf(BaseSqlgStrategy.EMIT_LABEL_SUFFIX) + BaseSqlgStrategy.EMIT_LABEL_SUFFIX.length());
            } else {
                throw new IllegalStateException();
            }
            for (Pair<Object, Optional<Long>> labeledElementPair : labeledElements) {
                if (addT && labeledElementPair.getLeft() == t) {
                    addT = false;
                }
                Set<Object> allLabeledElementsAsSet = allLabeledElementMap.get(pathLabel);
                if (allLabeledElementsAsSet == null) {
                    allLabeledElementsAsSet = new HashSet<>();
                    allLabeledElementMap.put(pathLabel, allLabeledElementsAsSet);
                }
                if (!allLabeledElementsAsSet.contains(labeledElementPair.getLeft())) {
                    currentPath = currentPath.extend(labeledElementPair.getLeft(), Collections.singleton(realLabel));
                    allLabeledElementsAsSet.add(labeledElementPair.getLeft());
                    if (pathLabel.endsWith(BaseSqlgStrategy.EMIT_LABEL_SUFFIX)) {
                        this.toEmit.add(Pair.of(currentPath.clone(), labeledElementPair));
                    }
                } else {
                    currentPath.addLabel(realLabel);
                }
            }
        }
        if (addT && !(t instanceof SqlgGraphStepWithPathTraverserGenerator.Dummy)) {
            //tp relies on all elements traversed being on the path.
            //if the element is not labelled put it on the path
            currentPath = currentPath.clone().extend(t);
        }
        this.path = currentPath;
    }

    @Override
    public void setPath(Path path) {
        this.path = path;
    }

    @Override
    public Path getPath() {
        return this.path;
    }
}