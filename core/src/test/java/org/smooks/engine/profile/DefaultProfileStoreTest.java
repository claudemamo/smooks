/*-
 * ========================LICENSE_START=================================
 * Smooks Core
 * %%
 * Copyright (C) 2020 Smooks
 * %%
 * Licensed under the terms of the Apache License Version 2.0, or
 * the GNU Lesser General Public License version 3.0 or later.
 * 
 * SPDX-License-Identifier: Apache-2.0 OR LGPL-3.0-or-later
 * 
 * ======================================================================
 * 
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
 * 
 * ======================================================================
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * =========================LICENSE_END==================================
 */
package org.smooks.engine.profile;

import org.junit.Test;
import org.smooks.api.profile.ProfileStore;
import org.smooks.api.profile.UnknownProfileMemberException;

import static org.junit.Assert.*;

/**
 * 
 * @author tfennelly
 */
public class DefaultProfileStoreTest {

	@Test
	public void testAddGetProfileSet() {
		ProfileStore store = new DefaultProfileStore();
		DefaultProfileSet set1 = new DefaultProfileSet("device1");
		DefaultProfileSet set2 = new DefaultProfileSet("device2");

		try {
			DefaultProfileStore.UnitTest.addProfileSet(store, null);
			fail("no IllegalArgumentException on null devicename");
		} catch (IllegalArgumentException e) {
		}

		DefaultProfileStore.UnitTest.addProfileSet(store, set1);
		DefaultProfileStore.UnitTest.addProfileSet(store, set2);
		try {
			store.getProfileSet("device3");
			fail("no UnknownProfileMemberException");
		} catch (UnknownProfileMemberException e) {
		}
		try {
			assertEquals(set1, store.getProfileSet("device1"));
		} catch (UnknownProfileMemberException e1) {
			fail("failed to get set");
		}
		try {
			assertEquals(set2, store.getProfileSet("device2"));
		} catch (UnknownProfileMemberException e1) {
			fail("failed to get set");
		}
		try {
			DefaultProfileStore.UnitTest.addProfileSet(store, set1);
			assertEquals(set1, store.getProfileSet("device2"));
		} catch (UnknownProfileMemberException e1) {
			fail("failed to get set");
		}
	}
}