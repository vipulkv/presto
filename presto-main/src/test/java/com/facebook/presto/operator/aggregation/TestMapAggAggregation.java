/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.operator.aggregation;

import com.facebook.presto.metadata.MetadataManager;
import com.facebook.presto.metadata.Signature;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.block.BlockBuilderStatus;
import com.facebook.presto.spi.type.StandardTypes;
import com.facebook.presto.type.ArrayType;
import com.facebook.presto.type.MapType;
import com.facebook.presto.type.RowType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.facebook.presto.block.BlockAssertions.createBooleansBlock;
import static com.facebook.presto.block.BlockAssertions.createDoublesBlock;
import static com.facebook.presto.block.BlockAssertions.createLongsBlock;
import static com.facebook.presto.block.BlockAssertions.createStringArraysBlock;
import static com.facebook.presto.block.BlockAssertions.createStringsBlock;
import static com.facebook.presto.operator.aggregation.AggregationTestUtils.assertAggregation;
import static com.facebook.presto.operator.aggregation.MapAggregation.NAME;
import static com.facebook.presto.operator.scalar.TestingRowConstructor.testRowBigintBigint;
import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.spi.type.BooleanType.BOOLEAN;
import static com.facebook.presto.spi.type.DoubleType.DOUBLE;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static com.facebook.presto.util.StructuralTestUtil.mapBlockOf;

public class TestMapAggAggregation
{
    private static final MetadataManager metadata = MetadataManager.createTestMetadataManager();

    @Test
    public void testDuplicateKeysValues()
            throws Exception
    {
        MapType mapType = new MapType(DOUBLE, VARCHAR);
        InternalAggregationFunction aggFunc = metadata.getExactFunction(new Signature(NAME, mapType.getTypeSignature().toString(), StandardTypes.DOUBLE, StandardTypes.VARCHAR)).getAggregationFunction();
        assertAggregation(
                aggFunc,
                1.0,
                ImmutableMap.of(1.0, "a"),
                createDoublesBlock(1.0, 1.0, 1.0),
                createStringsBlock("a", "b", "c"));

        mapType = new MapType(DOUBLE, BIGINT);
        aggFunc = metadata.getExactFunction(new Signature(NAME, mapType.getTypeSignature().toString(), StandardTypes.DOUBLE, StandardTypes.BIGINT)).getAggregationFunction();
        assertAggregation(
                aggFunc,
                1.0,
                ImmutableMap.of(1.0, 99L, 2.0, 99L, 3.0, 99L),
                createDoublesBlock(1.0, 2.0, 3.0),
                createLongsBlock(99L, 99L, 99L));
    }

    @Test
    public void testSimpleMaps()
            throws Exception
    {
        MapType mapType = new MapType(DOUBLE, VARCHAR);
        InternalAggregationFunction aggFunc = metadata.getExactFunction(new Signature(NAME, mapType.getTypeSignature().toString(), StandardTypes.DOUBLE, StandardTypes.VARCHAR)).getAggregationFunction();
        assertAggregation(
                aggFunc,
                1.0,
                ImmutableMap.of(1.0, "a", 2.0, "b", 3.0, "c"),
                createDoublesBlock(1.0, 2.0, 3.0),
                createStringsBlock("a", "b", "c"));

        mapType = new MapType(DOUBLE, BIGINT);
        aggFunc = metadata.getExactFunction(new Signature(NAME, mapType.getTypeSignature().toString(), StandardTypes.DOUBLE, StandardTypes.BIGINT)).getAggregationFunction();
        assertAggregation(
                aggFunc,
                1.0,
                ImmutableMap.of(1.0, 3L, 2.0, 2L, 3.0, 1L),
                createDoublesBlock(1.0, 2.0, 3.0),
                createLongsBlock(3L, 2L, 1L));

        mapType = new MapType(DOUBLE, BOOLEAN);
        aggFunc = metadata.getExactFunction(new Signature(NAME, mapType.getTypeSignature().toString(), StandardTypes.DOUBLE, StandardTypes.BOOLEAN)).getAggregationFunction();
        assertAggregation(
                aggFunc,
                1.0,
                ImmutableMap.of(1.0, true, 2.0, false, 3.0, false),
                createDoublesBlock(1.0, 2.0, 3.0),
                createBooleansBlock(true, false, false));
    }

    @Test
    public void testNull()
            throws Exception
    {
        InternalAggregationFunction doubleDouble = metadata.getExactFunction(new Signature(NAME, new MapType(DOUBLE, DOUBLE).getTypeSignature().toString(), StandardTypes.DOUBLE, StandardTypes.DOUBLE)).getAggregationFunction();
        assertAggregation(
                doubleDouble,
                1.0,
                ImmutableMap.of(1.0, 2.0),
                createDoublesBlock(1.0, null, null),
                createDoublesBlock(2.0, 3.0, 4.0));

        assertAggregation(
                doubleDouble,
                1.0,
                null,
                createDoublesBlock(null, null, null),
                createDoublesBlock(2.0, 3.0, 4.0));

        Map<Double, Double> expected = new LinkedHashMap<>();
        expected.put(1.0, null);
        expected.put(2.0, null);
        expected.put(3.0, null);
        assertAggregation(
                doubleDouble,
                1.0,
                expected,
                createDoublesBlock(1.0, 2.0, 3.0),
                createDoublesBlock(null, null, null));
    }

    @Test
    public void testDoubleArrayMap()
            throws Exception
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        MapType mapType = new MapType(DOUBLE, arrayType);
        InternalAggregationFunction aggFunc = metadata.getExactFunction(new Signature(NAME,
                                                                                    mapType.getTypeSignature().toString(),
                                                                                    StandardTypes.DOUBLE,
                                                                                    arrayType.getTypeSignature().toString())).getAggregationFunction();

        assertAggregation(
                aggFunc,
                1.0,
                ImmutableMap.of(1.0, ImmutableList.of("a", "b"),
                        2.0, ImmutableList.of("c", "d"),
                        3.0, ImmutableList.of("e", "f")),
                createDoublesBlock(1.0, 2.0, 3.0),
                createStringArraysBlock(ImmutableList.of(ImmutableList.of("a", "b"), ImmutableList.of("c", "d"), ImmutableList.of("e", "f"))));
    }

    @Test
    public void testDoubleMapMap()
            throws Exception
    {
        MapType innerMapType = new MapType(VARCHAR, VARCHAR);
        MapType mapType = new MapType(DOUBLE, innerMapType);
        InternalAggregationFunction aggFunc = metadata.getExactFunction(new Signature(NAME,
                mapType.getTypeSignature().toString(),
                StandardTypes.DOUBLE,
                innerMapType.getTypeSignature().toString())).getAggregationFunction();

        BlockBuilder builder = innerMapType.createBlockBuilder(new BlockBuilderStatus(), 3);
        innerMapType.writeObject(builder, mapBlockOf(VARCHAR, VARCHAR, ImmutableMap.of("a", "b")));
        innerMapType.writeObject(builder, mapBlockOf(VARCHAR, VARCHAR, ImmutableMap.of("c", "d")));
        innerMapType.writeObject(builder, mapBlockOf(VARCHAR, VARCHAR, ImmutableMap.of("e", "f")));

        assertAggregation(
                aggFunc,
                1.0,
                ImmutableMap.of(1.0, ImmutableMap.of("a", "b"),
                        2.0, ImmutableMap.of("c", "d"),
                        3.0, ImmutableMap.of("e", "f")),
                createDoublesBlock(1.0, 2.0, 3.0),
                builder.build());
    }

    @Test
    public void testDoubleRowMap()
            throws Exception
    {
        RowType innerRowType = new RowType(ImmutableList.of(BIGINT, DOUBLE), Optional.of(ImmutableList.of("f1", "f2")));
        MapType mapType = new MapType(DOUBLE, innerRowType);
        InternalAggregationFunction aggFunc = metadata.getExactFunction(new Signature(NAME,
                mapType.getTypeSignature().toString(),
                StandardTypes.DOUBLE,
                innerRowType.getTypeSignature().toString())).getAggregationFunction();

        BlockBuilder builder = innerRowType.createBlockBuilder(new BlockBuilderStatus(), 3);
        innerRowType.writeObject(builder, testRowBigintBigint(1L, 1.0));
        innerRowType.writeObject(builder, testRowBigintBigint(2L, 2.0));
        innerRowType.writeObject(builder, testRowBigintBigint(3L, 3.0));

        assertAggregation(
                aggFunc,
                1.0,
                ImmutableMap.of(1.0, ImmutableList.of(1L, 1.0),
                        2.0, ImmutableList.of(2L, 2.0),
                        3.0, ImmutableList.of(3L, 3.0)),
                createDoublesBlock(1.0, 2.0, 3.0),
                builder.build());
    }

    @Test
    public void testArrayDoubleMap()
            throws Exception
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        MapType mapType = new MapType(arrayType, DOUBLE);
        InternalAggregationFunction aggFunc = metadata.getExactFunction(new Signature(
                NAME,
                mapType.getTypeSignature().toString(),
                arrayType.getTypeSignature().toString(),
                StandardTypes.DOUBLE
        )).getAggregationFunction();

        assertAggregation(
                aggFunc,
                1.0,
                ImmutableMap.of(
                        ImmutableList.of("a", "b"), 1.0,
                        ImmutableList.of("c", "d"), 2.0,
                        ImmutableList.of("e", "f"), 3.0),
                createStringArraysBlock(ImmutableList.of(ImmutableList.of("a", "b"), ImmutableList.of("c", "d"), ImmutableList.of("e", "f"))),
                createDoublesBlock(1.0, 2.0, 3.0));
    }
}
