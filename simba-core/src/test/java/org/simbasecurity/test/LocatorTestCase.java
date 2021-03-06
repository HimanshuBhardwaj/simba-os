/*
 * Copyright 2013 Simba Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.simbasecurity.test;

import org.junit.Before;
import org.simbasecurity.core.audit.Audit;
import org.simbasecurity.core.locator.Locator;

public abstract class LocatorTestCase {

    private Locator locator;

    @Before
    public void init() {
        backupOriginalGlobalContextLocatorAndSetMock();
        implantMock(Audit.class);
    }

    private void backupOriginalGlobalContextLocatorAndSetMock() {
        locator = TestLocator.createLocatorMock();
    }

    protected <B> B implantMock(Class<B> type) {
        return TestLocator.implantMock(locator, type);
    }

    protected <B> B implantMockLocatingByNameOnly(Class<B> type, String name) {
        return TestLocator.implantMockLocatingByNameOnly(locator, type, name);
    }

    protected <B> B implant(Class<B> type, B instance) {
        return TestLocator.implant(locator, type, instance);
    }

}