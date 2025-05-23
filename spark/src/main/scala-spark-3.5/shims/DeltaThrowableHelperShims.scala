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

package org.apache.spark.sql.delta

import org.apache.spark.SparkThrowable
import org.apache.spark.sql.catalyst.TableIdentifier
import org.apache.spark.sql.errors.QueryCompilationErrors

object DeltaThrowableHelperShims {
  /**
   * Handles a breaking change (SPARK-46810) between Spark 3.5 and Spark Master (4.0) where
   * `error-classes.json` was renamed to `error-conditions.json`.
   */
  val SPARK_ERROR_CLASS_SOURCE_FILE = "error/error-classes.json"

  def showColumnsWithConflictDatabasesError(
      db: Seq[String], v1TableName: TableIdentifier): Throwable = {
    QueryCompilationErrors.showColumnsWithConflictDatabasesError(db, v1TableName)
  }
}

trait DeltaThrowableConditionShim extends SparkThrowable {
  def getCondition(): String = getErrorClass()
  override def getErrorClass(): String
}
