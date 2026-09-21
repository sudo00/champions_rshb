// Included inline by report_photo_batch.py: the report remains a single offline file.
function createFileAutosave() {
    let handle = null, rememberedHandle = null, expectedText = null;
    let snapshot = annotationSnapshot(), revision = 0, savedRevision = 0;
    let writing = false, connecting = false, timer = null, error = '', savedAt = '';
    const supported = window.isSecureContext && typeof window.showSaveFilePicker === 'function';
    const button = $('enable-autosave'), status = $('file-status');

    function annotationSnapshot() {
        return JSON.stringify(data.images.map(r => annotations[r.image_id]));
    }

    function refresh() {
        button.disabled = !supported || writing || connecting;
        button.textContent = handle ? (error ? 'Возобновить автосохранение' : 'Сохранить сейчас')
            : rememberedHandle ? 'Возобновить автосохранение' : 'Включить автосохранение JSON';
        $('change-save-file').hidden = !handle && !rememberedHandle;
        $('change-save-file').disabled = writing || connecting;
        status.classList.toggle('error', Boolean(error) || invalidRows.size > 0);
        if (!supported) {
            status.textContent = 'Запись в файл недоступна в этом браузере. Черновик сохраняется в браузере; для JSON используйте скачивание или откройте страницу в Chrome/Edge на компьютере.';
        } else if (connecting) {
            status.textContent = 'Подключаю JSON…';
        } else if (invalidRows.size) {
            status.textContent = 'Есть незавершённые или неверные поля. Исправьте их: автосохранение JSON приостановлено.';
        } else if (error) {
            status.textContent = 'JSON не сохранён: ' + error + ' Черновик остаётся в браузере.';
        } else if (writing) {
            status.textContent = 'Сохраняю в ' + handle.name + '…';
        } else if (handle && revision !== savedRevision) {
            status.textContent = 'Изменения ожидают записи в ' + handle.name + '…';
        } else if (handle) {
            status.textContent = 'Сохранено в ' + handle.name + (savedAt ? ' · ' + savedAt : '') + '. Автосохранение включено.';
        } else if (rememberedHandle) {
            status.textContent = 'Выбран файл ' + rememberedHandle.name + '. Нажмите «Возобновить автосохранение» после открытия страницы.';
        } else {
            status.textContent = 'Черновик сохраняется автоматически в браузере. Чтобы изменения записывались в один JSON на диске, включите автосохранение.';
        }
    }

    function changed() {
        const current = annotationSnapshot();
        if (snapshot !== current) {
            snapshot = current;
            revision += 1;
        }
        schedule();
    }

    function schedule() {
        clearTimeout(timer);
        refresh();
        if (handle && revision !== savedRevision && !writing && !connecting && !error && !invalidRows.size) {
            timer = setTimeout(write, 450);
        }
    }

    async function write() {
        clearTimeout(timer);
        if (!handle || writing || connecting || error || invalidRows.size || revision === savedRevision) return;
        writing = true;
        const target = handle, writingRevision = revision;
        const text = JSON.stringify(exportValue(), null, 2) + '\n';
        let stream;
        refresh();
        try {
            if (await target.queryPermission({mode: 'readwrite'}) !== 'granted') {
                throw Error('Нужно снова разрешить запись. Нажмите «Возобновить автосохранение».');
            }
            // Catch edits made in another tab/editor before replacing the file.
            const currentText = await (await target.getFile()).text();
            if (currentText !== expectedText) {
                throw Error('Файл изменён вне этой страницы. Нажмите «Возобновить автосохранение» и выберите версию.');
            }
            stream = await target.createWritable();
            await stream.write(text);
            await stream.close();
            stream = null;
            expectedText = text;
            savedRevision = writingRevision;
            savedAt = new Date().toLocaleTimeString('ru');
        } catch (failure) {
            if (stream) { try { await stream.abort(); } catch {} }
            error = failure.message || 'Не удалось записать файл.';
        } finally {
            writing = false;
            // Changes made during the write get their own subsequent write.
            schedule();
        }
    }

    async function storedHandle(value) {
        const db = await new Promise((resolve, reject) => {
            const request = indexedDB.open('wine-photo-review-files', 1);
            request.onupgradeneeded = () => request.result.createObjectStore('handles');
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
        try {
            return await new Promise((resolve, reject) => {
                const transaction = db.transaction('handles', value ? 'readwrite' : 'readonly');
                const store = transaction.objectStore('handles');
                const request = value ? store.put(value, storageKey) : store.get(storageKey);
                transaction.oncomplete = () => resolve(request.result);
                transaction.onerror = () => reject(transaction.error);
                transaction.onabort = () => reject(transaction.error);
            });
        } finally { db.close(); }
    }

    function chooseVersion(incoming) {
        const count = rows => rows.filter(a => a.status !== 'not_reviewed' || a.notes).length;
        const dialog = $('file-conflict');
        $('conflict-description').textContent = `В выбранном JSON есть другая разметка. Заполнено в файле: ${count(incoming)}; в браузере: ${count(Object.values(annotations))}. Выберите, какую версию продолжить.`;
        return new Promise(resolve => {
            function finish(value) { dialog.close(); resolve(value); }
            $('use-file-version').onclick = () => finish('file');
            $('use-browser-version').onclick = () => finish('browser');
            $('cancel-file-version').onclick = () => finish('cancel');
            dialog.oncancel = event => { event.preventDefault(); finish('cancel'); };
            dialog.showModal();
        });
    }

    async function connectFile(forcePicker = false) {
        if (!supported || writing || connecting || !flush()) return;
        clearTimeout(timer);
        connecting = true;
        refresh();
        try {
            let selected = !forcePicker && (handle || rememberedHandle);
            if (!selected) {
                // Call the picker directly from the button's user gesture.
                selected = await window.showSaveFilePicker({
                    id: 'wine-review-annotations', suggestedName: 'organizers_annotations.json',
                    types: [{description: 'Разметка фотографий (JSON)', accept: {'application/json': ['.json']}}],
                    excludeAcceptAllOption: true,
                });
            } else if (await selected.requestPermission({mode: 'readwrite'}) !== 'granted') {
                throw Error('Разрешение на запись не получено.');
            }
            if (!selected.name.toLowerCase().endsWith('.json')) throw Error('Выберите файл с расширением .json.');
            const file = await selected.getFile();
            const text = await file.text();
            let incoming = null, choice = 'browser';
            if (text.trim()) {
                // Validate before touching an existing file. Other datasets are rejected.
                incoming = validateValue(JSON.parse(text));
                if (incoming.length !== data.images.length) throw Error('В выбранном JSON неполный набор фотографий. Используйте импорт разметки или другой файл.');
                const lookup = new Map(incoming.map(a => [a.image_id, a]));
                incoming = data.images.map(r => lookup.get(r.image_id));
                const unchangedConnectedFile = selected === handle && text === expectedText;
                if (JSON.stringify(incoming) !== annotationSnapshot() && !unchangedConnectedFile) {
                    const hasDraft = Object.values(annotations).some(a => a.status !== 'not_reviewed' || a.notes);
                    choice = hasDraft ? await chooseVersion(incoming) : 'file';
                    if (choice === 'cancel') return;
                }
            }
            if (choice === 'file') {
                for (const a of incoming) annotations[a.image_id] = a;
                persist();
                render();
            }
            handle = selected;
            rememberedHandle = selected;
            expectedText = text;
            error = '';
            // A newly selected file must receive the current snapshot even if unchanged.
            savedRevision = -1;
            try { await storedHandle(selected); }
            catch { notify('Файл подключён. После повторного открытия страницы может потребоваться выбрать его снова.'); }
        } catch (failure) {
            if (failure.name !== 'AbortError') {
                error = failure.message || 'Не удалось подключить файл.';
                notify(error);
            }
        } finally {
            connecting = false;
            refresh();
            await write();
        }
    }

    button.onclick = () => connectFile();
    $('change-save-file').onclick = () => connectFile(true);
    window.addEventListener('beforeunload', event => {
        if (invalidRows.size || (handle && (writing || revision !== savedRevision))) {
            event.preventDefault();
            event.returnValue = '';
        }
    });
    document.addEventListener('visibilitychange', () => {
        if (document.hidden && flush()) void write();
    });
    refresh();
    if (supported) storedHandle().then(value => {
        if (!handle && !connecting && value) rememberedHandle = value;
        refresh();
    }).catch(() => {});
    return {changed, refresh};
}

fileAutosave = createFileAutosave();
