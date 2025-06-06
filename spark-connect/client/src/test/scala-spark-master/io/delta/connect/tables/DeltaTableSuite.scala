/*
 * Copyright (2024) The Delta Lake Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.delta.tables

import java.io.File
import java.text.SimpleDateFormat

import org.apache.commons.io.FileUtils

import org.apache.spark.sql.Row
import org.apache.spark.sql.functions.{col, lit}
import org.apache.spark.sql.test.DeltaQueryTest

// TODO(hvanhovell) All tests here are temporary disabled. While the code compiles, we need to a
//  Spark PR to land (https://github.com/apache/spark/pull/49971), and make a couple of changes to
//  make tests work. This will be done in a follow-up PR.
class DeltaTableSuite extends DeltaQueryTest with RemoteSparkSession {
  private lazy val testData = spark.range(100).toDF("value")

  ignore("forPath") {
    withTempPath { dir =>
      testData.write.format("delta").save(dir.getAbsolutePath)
      checkAnswer(
        DeltaTable.forPath(spark, dir.getAbsolutePath).toDF,
        testData.collect().toSeq
      )
    }
  }

  ignore("forName") {
    withTable("deltaTable") {
      testData.write.format("delta").saveAsTable("deltaTable")
      checkAnswer(
        DeltaTable.forName(spark, "deltaTable").toDF,
        testData.collect().toSeq
      )
    }
  }

  ignore("as") {
    withTempPath { dir =>
      testData.write.format("delta").save(dir.getAbsolutePath)
      checkAnswer(
        DeltaTable.forPath(spark, dir.getAbsolutePath).as("tbl").toDF.select("tbl.value"),
        testData.select("value").collect().toSeq
      )
    }
  }

  ignore("vacuum") {
    Seq("true", "false").foreach{ deltaFormatCheckEnabled =>
      withSQLConf(
        "spark.databricks.delta.formatCheck.cache.enabled" -> deltaFormatCheckEnabled) {
          withTempPath { dir =>
          testData.write.format("delta").save(dir.getAbsolutePath)
          val table = io.delta.tables.DeltaTable.forPath(spark, dir.getAbsolutePath)

          // create a uncommitted file.
          val notCommittedFile = "notCommittedFile.json"
          val file = new File(dir, notCommittedFile)
          FileUtils.write(file, "gibberish")
          // set to ancient time so that the file is eligible to be vacuumed.
          file.setLastModified(0)
          assert(file.exists())

          table.vacuum()

          val file2 = new File(dir, notCommittedFile)
          assert(!file2.exists())
        }
      }
    }
  }

  ignore("history") {
    val session = spark
    import session.implicits._

    withTempPath { dir =>
      Seq(1, 2, 3).toDF().write.format("delta").save(dir.getAbsolutePath)
      Seq(4, 5).toDF().write.format("delta").mode("append").save(dir.getAbsolutePath)

      val table = DeltaTable.forPath(spark, dir.getAbsolutePath)
      checkAnswer(
        table.history().select("version"),
        Seq(Row(0L), Row(1L))
      )
    }
  }

  ignore("detail") {
    val session = spark
    import session.implicits._

    withTempPath { dir =>
      Seq(1, 2, 3).toDF().write.format("delta").save(dir.getAbsolutePath)

      val deltaTable = DeltaTable.forPath(spark, dir.getAbsolutePath)
      checkAnswer(
        deltaTable.detail().select("format"),
        Seq(Row("delta"))
      )
    }
  }

  ignore("isDeltaTable - path - with _delta_log dir") {
    withTempPath { dir =>
      testData.write.format("delta").save(dir.getAbsolutePath)
      assert(DeltaTable.isDeltaTable(spark, dir.getAbsolutePath))
    }
  }

  ignore("isDeltaTable - path - with empty _delta_log dir") {
    withTempPath { dir =>
      new File(dir, "_delta_log").mkdirs()
      assert(!DeltaTable.isDeltaTable(spark, dir.getAbsolutePath))
    }
  }

  ignore("isDeltaTable - path - with no _delta_log dir") {
    withTempPath { dir =>
      assert(!DeltaTable.isDeltaTable(spark, dir.getAbsolutePath))
    }
  }

  ignore("isDeltaTable - path - with non-existent dir") {
    withTempPath { dir =>
      assert(!DeltaTable.isDeltaTable(spark, dir.getAbsolutePath))
    }
  }

  ignore("isDeltaTable - with non-Delta table path") {
    withTempPath { dir =>
      testData.write.format("parquet").mode("overwrite").save(dir.getAbsolutePath)
      assert(!DeltaTable.isDeltaTable(spark, dir.getAbsolutePath))
    }
  }

  ignore("generate") {
    withTempPath { dir =>
      val path = dir.getAbsolutePath
      testData.toDF().write.format("delta").save(path)
      val table = DeltaTable.forPath(spark, path)
      val manifestDir = new File(dir, "_symlink_format_manifest")
      assert(!manifestDir.exists())
      table.generate("symlink_format_manifest")
      assert(manifestDir.exists())
    }
  }

  ignore("delete") {
    val session = spark
    import session.implicits._
    withTempPath { dir =>
      val path = dir.getAbsolutePath
      Seq(("a", 1), ("b", 2), ("c", 3), ("d", 4)).toDF("key", "value")
        .write.format("delta").save(path)
      val deltaTable = DeltaTable.forPath(spark, path)

      deltaTable.delete("key = 'a'")
      checkAnswer(deltaTable.toDF, Seq(Row("b", 2), Row("c", 3), Row("d", 4)))

      deltaTable.delete(col("key") === lit("b"))
      checkAnswer(deltaTable.toDF, Seq(Row("c", 3), Row("d", 4)))

      deltaTable.delete()
      checkAnswer(deltaTable.toDF, Nil)
    }
  }

  ignore("update") {
    val session = spark
    import session.implicits._
    withTempPath { dir =>
      val path = dir.getAbsolutePath
      Seq(("a", 1), ("b", 2), ("c", 3), ("d", 4)).toDF("key", "value")
        .write.format("delta").save(path)
      val deltaTable = DeltaTable.forPath(spark, path)

      deltaTable.updateExpr("key = 'a' or key = 'b'", Map("value" -> "1"))
      checkAnswer(deltaTable.toDF, Seq(Row("a", 1), Row("b", 1), Row("c", 3), Row("d", 4)))

      deltaTable.update(col("key") === lit("a") || col("key") === lit("b"), Map("value" -> lit(0)))
      checkAnswer(deltaTable.toDF, Seq(Row("a", 0), Row("b", 0), Row("c", 3), Row("d", 4)))

      deltaTable.updateExpr(Map("value" -> "-1"))
      checkAnswer(deltaTable.toDF, Seq(Row("a", -1), Row("b", -1), Row("c", -1), Row("d", -1)))

      deltaTable.update(Map("value" -> lit(37)))
      checkAnswer(deltaTable.toDF, Seq(Row("a", 37), Row("b", 37), Row("c", 37), Row("d", 37)))
    }
  }

  private def getTimestampForVersion(path: String, version: Long): String = {
    val logPath = new File(path, "_delta_log")
    val file = new File(logPath, f"$version%020d.json")
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    sdf.format(file.lastModified())
  }

  ignore("clone") {
    withTempPath { dir =>
      val baseDir = dir.getAbsolutePath

      val srcDir = new File(baseDir, "source").getCanonicalPath
      val dstDir = new File(baseDir, "destination").getCanonicalPath

      spark.range(10).write.format("delta").save(srcDir)

      val srcTable = io.delta.tables.DeltaTable.forPath(spark, srcDir)
      srcTable.clone(dstDir, true)

      checkAnswer(
        spark.read.format("delta").load(dstDir),
        spark.read.format("delta").load(srcDir))
    }
  }

  ignore("cloneAtVersion/timestamp - with filesystem options") {
    val session = spark
    import session.implicits._
    withTempPath { dir =>
      val baseDir = dir.getAbsolutePath

      val srcDir = new File(baseDir, "source").getCanonicalPath
      val dstDir = new File(baseDir, "destination").getCanonicalPath

      val df1 = Seq(1, 2, 3).toDF("id")
      val df2 = Seq(4, 5).toDF("id")
      val df3 = Seq(6, 7).toDF("id")

      // version 0.
      df1.write.format("delta").save(srcDir)
      // version 1.
      df2.write.format("delta").mode("append").save(srcDir)
      // version 2.
      df3.write.format("delta").mode("append").save(srcDir)

      val srcTable = io.delta.tables.DeltaTable.forPath(spark, srcDir)

      srcTable.cloneAtVersion(1, dstDir, true)

      checkAnswer(
        spark.read.format("delta").load(dstDir),
        df1.union(df2))

      val timestamp = getTimestampForVersion(srcDir, version = 0)
      srcTable.cloneAtTimestamp(timestamp, dstDir, isShallow = true, replace = true)

      checkAnswer(
        spark.read.format("delta").load(dstDir),
        df1)
    }
  }

  ignore("restore") {
    val session = spark
    import session.implicits._
    withTempPath { dir =>
      val path = dir.getPath

      val df1 = Seq(1, 2, 3).toDF("id")
      val df2 = Seq(4, 5).toDF("id")
      val df3 = Seq(6, 7).toDF("id")

      // version 0.
      df1.write.format("delta").save(path)
      // version 1.
      df2.write.format("delta").mode("append").save(path)
      // version 2.
      df3.write.format("delta").mode("append").save(path)

      checkAnswer(
        spark.read.format("delta").load(path),
        df1.union(df2).union(df3))

      val deltaTable = io.delta.tables.DeltaTable.forPath(spark, path)
      deltaTable.restoreToVersion(1)

      checkAnswer(
        spark.read.format("delta").load(path),
        df1.union(df2))

      val deltaTable2 = io.delta.tables.DeltaTable.forPath(spark, path)
      val timestamp = getTimestampForVersion(path, version = 0)
      deltaTable2.restoreToTimestamp(timestamp)

      checkAnswer(
        spark.read.format("delta").load(path),
        df1)
    }
  }

  ignore("upgradeTableProtocol") {
    withTempPath { dir =>
      val path = dir.getAbsolutePath
      testData.write.format("delta").save(path)
      val table = DeltaTable.forPath(spark, path)
      table.upgradeTableProtocol(1, 2)
      checkAnswer(
        table.history().select("version", "operation"),
        Seq(Row(0L, "WRITE"), Row(1L, "SET TBLPROPERTIES"))
      )
    }
  }
}
