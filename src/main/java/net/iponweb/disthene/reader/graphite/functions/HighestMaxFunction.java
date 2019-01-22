package net.iponweb.disthene.reader.graphite.functions;

import net.iponweb.disthene.reader.beans.TimeSeries;
import net.iponweb.disthene.reader.exceptions.EvaluationException;
import net.iponweb.disthene.reader.exceptions.InvalidArgumentException;
import net.iponweb.disthene.reader.exceptions.TimeSeriesNotAlignedException;
import net.iponweb.disthene.reader.graphite.Target;
import net.iponweb.disthene.reader.graphite.evaluation.TargetEvaluator;
import net.iponweb.disthene.reader.utils.CollectionUtils;
import net.iponweb.disthene.reader.utils.TimeSeriesUtils;

import java.util.*;

/**
 * @author Andrei Ivanov
 */
public class HighestMaxFunction extends DistheneFunction {


    public HighestMaxFunction(String text) {
        super(text, "highestMax");
    }

    @Override
    public List<TimeSeries> evaluate(TargetEvaluator evaluator) throws EvaluationException {
        int number = ((Double) arguments.get(1)).intValue();
        if (number <= 0) return Collections.emptyList();

        List<TimeSeries> processedArguments = new ArrayList<>(evaluator.eval((Target) arguments.get(0)));

        if (processedArguments.size() == 0) return new ArrayList<>();

        if (!TimeSeriesUtils.checkAlignment(processedArguments)) {
            throw new TimeSeriesNotAlignedException();
        }

        SortedMap<Double, List<TimeSeries>> sorted = new TreeMap<>(Collections.reverseOrder());

        for(TimeSeries ts : processedArguments) {
            Double max = CollectionUtils.max(Arrays.asList(ts.getValues()));
            if (max != null) {
                if (sorted.get(max) == null) sorted.put(max, new ArrayList<TimeSeries>());
                sorted.get(max).add(ts);
            }
        }

        List<TimeSeries> result = new ArrayList<>();

        for(Map.Entry<Double, List<TimeSeries>> entry : sorted.entrySet()) {
            result.addAll(entry.getValue());
            if (result.size() >= number) break;
        }

        return result.subList(0, Math.min(number, result.size()));
    }

    @Override
    public void checkArguments() throws InvalidArgumentException {
        if (arguments.size() != 2) throw new InvalidArgumentException("highestMax: number of arguments is " + arguments.size() + ". Must be one.");
        if (!(arguments.get(0) instanceof Target)) throw new InvalidArgumentException("highestMax: argument is " + arguments.get(0).getClass().getName() + ". Must be series");
        if (!(arguments.get(1) instanceof Double)) throw new InvalidArgumentException("highestMax: argument is " + arguments.get(1).getClass().getName() + ". Must be a number");
    }
}