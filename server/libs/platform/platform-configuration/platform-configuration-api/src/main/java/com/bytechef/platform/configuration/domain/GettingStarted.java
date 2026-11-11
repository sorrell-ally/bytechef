/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bytechef.platform.configuration.domain;

import com.bytechef.platform.constant.PlatformType;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * @author Ivica Cardic
 */
@Table("getting_started")
public class GettingStarted {

    public enum CompletionType {
        COMPLETED, DISMISSED
    }

    @Column("completed_date")
    private Instant completedDate;

    @Column("completion_type")
    private int completionType;

    @Id
    private Long id;

    @Column
    private int type;

    public GettingStarted() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GettingStarted gettingStarted = (GettingStarted) o;

        return Objects.equals(id, gettingStarted.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public Instant getCompletedDate() {
        return completedDate;
    }

    public CompletionType getCompletionType() {
        return CompletionType.values()[completionType];
    }

    public Long getId() {
        return id;
    }

    public PlatformType getType() {
        return PlatformType.values()[type];
    }

    @Override
    public String toString() {
        return "GettingStarted{" +
            "id=" + id +
            ", type=" + type +
            ", completionType=" + completionType +
            ", completedDate=" + completedDate +
            '}';
    }
}
