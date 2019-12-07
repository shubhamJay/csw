package csw.params.core.formats;

import csw.params.core.generics.Parameter;
import csw.params.core.models.*;
import csw.params.events.Event;
import csw.params.events.EventName;
import csw.params.events.SystemEvent;
import csw.params.javadsl.JKeyType;
import csw.params.javadsl.JSubsystem;
import csw.time.core.models.UTCTime;
import io.bullet.borer.Cbor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.scalatestplus.junit.JUnitSuite;

import java.util.Arrays;
import java.util.List;

// DEOPSCSW-495: Protobuf serde fails for Java keys/parameters
@RunWith(value = Parameterized.class)
public class JCborTest extends JUnitSuite {

    private final String keyType;
    private final Parameter<?> param;

    public JCborTest(String keyType, Parameter<?> param) {
        this.keyType = keyType;
        this.param = param;
    }

    @Parameterized.Parameters(name = "{index}: KeyType={0}")
    public static List<Object[]> data() {
        Byte[] byteData = {1, 2, 3};
        Short[] shortData = {10, 20, 30};
        Long[] longData = {100L, 200L, 300L};
        Integer[] intData = {1000, 2000, 3000};
        Float[] floatData = {10000.10f, 20000.20f, 30000.30f};
        Double[] doubleData = {100000.100d, 200000.200d, 300000.300d};

        Byte[][] byteData2D = {{1, 2, 3}, {4, 5, 6}};
        Short[][] shortData2D = {{10, 20, 30}, {40, 50, 60}};
        Long[][] longData2D = {{100L, 200L, 300L}, {400L, 500L, 600L}};
        Integer[][] intData2D = {{1000, 2000, 3000}, {4000, 5000, 6000}};
        Float[][] floatData2D = {{10000.10f, 20000.20f, 30000.30f}, {40000.40f, 50000f, 60000f}};
        Double[][] doubleData2D = {{100000.100d, 200000.200d, 300000.300d}, {400000.400d, 500000d, 600000d}};

        MatrixData<Byte> byteMatrixData = MatrixData.fromJavaArrays(Byte.class, byteData2D);
        MatrixData<Short> shortMatrixData = MatrixData.fromJavaArrays(Short.class, shortData2D);
        MatrixData<Long> longMatrixData = MatrixData.fromJavaArrays(Long.class, longData2D);
        MatrixData<Integer> integerMatrixData = MatrixData.fromJavaArrays(Integer.class, intData2D);
        MatrixData<Float> floatMatrixData = MatrixData.fromJavaArrays(Float.class, floatData2D);
        MatrixData<Double> doubleMatrixData = MatrixData.fromJavaArrays(Double.class, doubleData2D);

        return Arrays.asList(new Object[][]{
                {"JChoiceKey", JKeyType.ChoiceKey().make("choiceKey", Choices.from("A", "B", "C")).set(new Choice("A"))},
                {"JRaDecKey", JKeyType.RaDecKey().make("raDecKey").set(new RaDec(10, 20))},
                {"JStructKey", JKeyType.StructKey().make("structKey").set(new Struct().add(JKeyType.RaDecKey().make("raDecKey").set(new RaDec(10, 20))))},
                {"JStringKey", JKeyType.StringKey().make("stringKey").set("str1", "str2")},
                {"JUTCTimeKey", JKeyType.UTCTimeKey().make("UTCTimeKey").set(UTCTime.now())},
                {"JTAITimeKey", JKeyType.UTCTimeKey().make("TAITimeKey").set(UTCTime.now())},
                {"JBooleanKey", JKeyType.BooleanKey().make("booleanKey").set(true)},
                {"JCharKey", JKeyType.CharKey().make("charKey").set('A', 'B')},
                {"JByteKey", JKeyType.ByteKey().make("byteKey").set(byteData)},
                {"JShortKey", JKeyType.ShortKey().make("shortKey").set(shortData)},
                {"JLongKey", JKeyType.LongKey().make("longKey").set(longData)},
                {"JIntKey", JKeyType.IntKey().make("intKey").set(intData)},
                {"JFloatKey", JKeyType.FloatKey().make("floatKey").set(floatData)},
                {"JDoubleKey", JKeyType.DoubleKey().make("doubleKey").set(doubleData)},
                {"JByteArrayKey", JKeyType.ByteArrayKey().make("byteArrayKey").set(ArrayData.fromJavaArray(byteData))},
                {"JShortArrayKey", JKeyType.ShortArrayKey().make("shortArrayKey").set(ArrayData.fromJavaArray(shortData))},
                {"JLongArrayKey", JKeyType.LongArrayKey().make("longArrayKey").set(ArrayData.fromJavaArray(longData))},
                {"JIntArrayKey", JKeyType.IntArrayKey().make("intArrayKey").set(ArrayData.fromJavaArray(intData))},
                {"JFloatArrayKey", JKeyType.FloatArrayKey().make("floatArrayKey").set(ArrayData.fromJavaArray(floatData))},
                {"JDoubleArrayKey", JKeyType.DoubleArrayKey().make("doubleArrayKey").set(ArrayData.fromJavaArray(doubleData))},
                {"JByteMatrixKey", JKeyType.ByteMatrixKey().make("byteMatrixKey").set(byteMatrixData)},
                {"JShortMatrixKey", JKeyType.ShortMatrixKey().make("shortMatrixKey").set(shortMatrixData)},
                {"JLongMatrixKey", JKeyType.LongMatrixKey().make("longMatrixKey").set(longMatrixData)},
                {"JIntMatrixKey", JKeyType.IntMatrixKey().make("intMatrixKey").set(integerMatrixData)},
                {"JFloatMatrixKey", JKeyType.FloatMatrixKey().make("floatMatrixKey").set(floatMatrixData)},
                {"JDoubleMatrixKey", JKeyType.DoubleMatrixKey().make("doubleMatrixKey").set(doubleMatrixData)}
        });
    }

    @Test
    public void shouldAbleToConvertToAndFromParameterAndEvent() {
        // ===== Test Parameter SERDE =====
        byte[] byteArray = Cbor.encode(param, ParamCodecs$.MODULE$.paramEncExistential()).toByteArray();
        Parameter parameterFromBytes = Cbor.decode(byteArray, JInput.FromByteArrayProvider()).to(ParamCodecs$.MODULE$.paramDecExistential()).value();

        Assert.assertEquals(param, parameterFromBytes);

        // ===== Test Event SERDE =====
        Prefix source = new Prefix(JSubsystem.WFOS(), "filter");
        EventName eventName = new EventName("move");
        SystemEvent originalEvent = new SystemEvent(source, eventName).add(param);

        byte[] bytes = EventCbor$.MODULE$.encode(originalEvent);
        Event eventFromBytes = EventCbor$.MODULE$.decode(bytes);
        Assert.assertEquals(originalEvent, eventFromBytes);
    }
}
