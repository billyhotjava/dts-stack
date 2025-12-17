/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import { extendedDayjs as dayjs } from '@superset-ui/core/utils/dates';
import { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import { ExplorePageState } from 'src/explore/types';
import 'dayjs/locale/en';
import 'dayjs/locale/zh-cn';

/* eslint-disable no-restricted-imports */
import { Locale } from 'antd/es/locale';

export const LOCALE_MAPPING = {
  en: () => import('antd/locale/en_US'),
  zh: () => import('antd/locale/zh_CN'),
};
/* eslint-enable no-restricted-imports */

const toDayjsLocale = (locale: string): string => {
  if (locale === 'zh') {
    return 'zh-cn';
  }
  return locale;
};

export const useLocale = (): Locale | undefined | null => {
  const [datePickerLocale, setDatePickerLocale] = useState<
    Locale | undefined | null
  >(null);

  // Retrieve the locale from Redux store
  const localFromFlaskBabel = useSelector(
    (state: ExplorePageState) => state?.common?.locale,
  );

  useEffect(() => {
    if (datePickerLocale === null) {
      if (
        localFromFlaskBabel &&
        LOCALE_MAPPING[localFromFlaskBabel as keyof typeof LOCALE_MAPPING]
      ) {
        LOCALE_MAPPING[localFromFlaskBabel as keyof typeof LOCALE_MAPPING]()
          .then((locale: { default: Locale }) => {
            setDatePickerLocale(locale.default);
            dayjs.locale(toDayjsLocale(localFromFlaskBabel));
          })
          .catch(() => setDatePickerLocale(undefined));
      } else {
        setDatePickerLocale(undefined);
      }
    }
  }, [datePickerLocale, localFromFlaskBabel]);

  return datePickerLocale;
};
