/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.js;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jetbrains.kotlin.idea.framework.KotlinJavaScriptLibraryDetectionUtil.isKotlinJavaScriptLibrary;
import static org.jetbrains.kotlin.idea.project.ProjectStructureUtil.isJsKotlinModule;
import static org.jetbrains.kotlin.utils.LibraryUtils.isKotlinJavascriptLibraryWithMetadata;

public class KotlinJavaScriptLibraryManager implements ProjectComponent, ModuleRootListener {

    public static KotlinJavaScriptLibraryManager getInstance(@NotNull Project project) {
        return project.getComponent(KotlinJavaScriptLibraryManager.class);
    }

    public static final String LIBRARY_NAME = "<Kotlin JavaScript library>";
    private final Object LIBRARY_COMMIT_LOCK = new Object();

    private Project myProject;

    private final AtomicBoolean myMuted = new AtomicBoolean(false);

    @SuppressWarnings("UnusedDeclaration")
    private KotlinJavaScriptLibraryManager(@NotNull Project project) {
        myProject = project;
    }

    @NotNull
    @Override
    public String getComponentName() {
        return "KotlinJavascriptLibraryManager";
    }

    @Override
    public void projectOpened() {
        myProject.getMessageBus().connect(myProject).subscribe(ProjectTopics.PROJECT_ROOTS, this);
        DumbService.getInstance(myProject).smartInvokeLater(new Runnable() {
            @Override
            public void run() {
                updateProjectLibrary();
            }
        });
    }

    @Override
    public void projectClosed() {
    }

    @Override
    public void initComponent() {
    }

    @Override
    public void disposeComponent() {
        myProject = null;
    }

    @Override
    public void beforeRootsChange(ModuleRootEvent event) {
    }

    @Override
    public void rootsChanged(ModuleRootEvent event) {
        if (myMuted.get()) return;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                DumbService.getInstance(myProject).runWhenSmart(new Runnable() {
                    @Override
                    public void run() {
                        updateProjectLibrary();
                    }
                });
            }
        }, ModalityState.NON_MODAL, myProject.getDisposed());
    }

    @TestOnly
    public void syncUpdateProjectLibrary() {
        updateProjectLibrary(true);
    }

    /**
     * @param synchronously may be true only in tests.
     */
    private void updateProjectLibrary(boolean synchronously) {
        if (myProject == null || myProject.isDisposed()) return;
        ApplicationManager.getApplication().assertReadAccessAllowed();

        for (Module module : ModuleManager.getInstance(myProject).getModules()) {
            if (!isModuleApplicable(module)) continue;

            if (!isJsKotlinModule(module)) {
                Library library = findLibraryByName(module, LIBRARY_NAME);
                if (library != null) {
                    resetLibraries(module, new ChangesToApply(), LIBRARY_NAME, synchronously);
                }
                continue;
            }

            final List<VirtualFile> clsRootFiles = new ArrayList<VirtualFile>();
            final List<VirtualFile> srcRootFiles = new ArrayList<VirtualFile>();

            ModuleRootManager.getInstance(module).orderEntries().librariesOnly().forEachLibrary(new Processor<Library>() {
                @Override
                public boolean process(Library library) {
                    if (!isKotlinJavaScriptLibrary(library)) return true;

                    boolean addSources = false;
                    for (VirtualFile clsRootFile : library.getFiles(OrderRootType.CLASSES)) {
                        String path = PathUtil.getLocalPath(clsRootFile);
                        assert path != null;

                        if (isKotlinJavascriptLibraryWithMetadata(new File(path))) {
                            VirtualFile classRoot = KotlinJavaScriptMetaFileSystem.getInstance().refreshAndFindFileByPath(path + "!/");
                            clsRootFiles.add(classRoot);
                            addSources = true;
                        }
                    }
                    if (addSources) {
                        srcRootFiles.addAll(new ArrayList<VirtualFile>(Arrays.asList(library.getFiles(OrderRootType.SOURCES))));
                    }

                    return true;
                }
            });

            ChangesToApply changesToApply = new ChangesToApply();

            for (VirtualFile file : clsRootFiles) {
                changesToApply.getClsUrlsToAdd().add(file.getUrl());
            }
            for (VirtualFile sourceFile : srcRootFiles) {
                changesToApply.getSrcUrlsToAdd().add(sourceFile.getUrl());
            }
            resetLibraries(module, changesToApply, LIBRARY_NAME, synchronously);
        }
    }

    private static boolean isModuleApplicable(Module module) {
        String id = ModuleType.get(module).getId();
        return ModuleTypeId.JAVA_MODULE.equals(id);
    }

    private void updateProjectLibrary() {
        updateProjectLibrary(false);
    }

    public void resetLibraries(Module module, ChangesToApply changesToApply, String libraryName, boolean synchronously) {
        applyChange(module, changesToApply, synchronously, libraryName);
    }

    private void applyChange(final Module module,
            final ChangesToApply changesToApply,
            boolean synchronously, final String libraryName) {
        if (synchronously) {    //for test only
            Application application = ApplicationManager.getApplication();
            if (!application.isUnitTestMode()) {
                throw new IllegalStateException("Synchronous library update may be done only in test mode");
            }
            AccessToken token = application.acquireWriteActionLock(KotlinJavaScriptLibraryManager.class);
            try {
                applyChangeImpl(module, changesToApply, libraryName);
            }
            finally {
                token.finish();
            }
        }
        else {
            final Runnable commit = new Runnable() {
                @Override
                public void run() {
                    applyChangeImpl(module, changesToApply, libraryName);
                }
            };
            Runnable commitInWriteAction = new Runnable() {
                @Override
                public void run() {
                    ApplicationManager.getApplication().runWriteAction(commit);
                }
            };
            ApplicationManager.getApplication().invokeLater(commitInWriteAction, myProject.getDisposed());
        }
    }

    private void applyChangeImpl(Module module, ChangesToApply changesToApply, String libraryName) {
        synchronized (LIBRARY_COMMIT_LOCK) {
            if (module.isDisposed()) {
                return;
            }
            ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
            LibraryTable.ModifiableModel libraryTableModel = model.getModuleLibraryTable().getModifiableModel();

            Library library = findLibraryByName(libraryTableModel, libraryName);

            if (library == null) {
                if (changesToApply.getClsUrlsToAdd().isEmpty()) {
                    model.dispose();
                    return;
                }

                library = libraryTableModel.createLibrary(libraryName);
            }

            if (changesToApply.getClsUrlsToAdd().isEmpty()) {
                libraryTableModel.removeLibrary(library);
                commitLibraries(null, libraryTableModel, model);
                return;
            }

            Library.ModifiableModel libraryModel = library.getModifiableModel();
            Set<String> classesUrls = new HashSet<String>(Arrays.asList(library.getUrls(OrderRootType.CLASSES)));
            Set<String> sourcesUrls = new HashSet<String>(Arrays.asList(library.getUrls(OrderRootType.SOURCES)));
            Set<String> existingClsUrls = new HashSet<String>();
            Set<String> existingSrcUrls = new HashSet<String>();
            existingClsUrls.addAll(classesUrls);
            existingSrcUrls.addAll(sourcesUrls);

            Set<String> newClsUrls = new HashSet<String>(changesToApply.getClsUrlsToAdd());
            Set<String> newSrcUrls = new HashSet<String>(changesToApply.getSrcUrlsToAdd());
            if (existingClsUrls.equals(newClsUrls) && existingSrcUrls.equals(newSrcUrls)) {
                model.dispose();
                Disposer.dispose(libraryModel);
                return;
            }

            for (String url : classesUrls) {
                libraryModel.removeRoot(url, OrderRootType.CLASSES);
            }
            for (String url : sourcesUrls) {
                libraryModel.removeRoot(url, OrderRootType.SOURCES);
            }

            for (String url : changesToApply.getClsUrlsToAdd()) {
                libraryModel.addRoot(url, OrderRootType.CLASSES);
            }
            for (String url : changesToApply.getSrcUrlsToAdd()) {
                libraryModel.addRoot(url, OrderRootType.SOURCES);
            }
            commitLibraries(libraryModel, libraryTableModel, model);
        }
    }

    private void commitLibraries(@Nullable Library.ModifiableModel libraryModel,
            LibraryTable.ModifiableModel tableModel,
            ModifiableRootModel model) {
        try {
            myMuted.set(true);
            if (libraryModel != null) {
                libraryModel.commit();
            }
            tableModel.commit();
            model.commit();
        }
        finally {
            myMuted.set(false);
        }
    }

    @Nullable
    private static Library findLibraryByName(LibraryTable.ModifiableModel libraryTableModel, String suggestedLibraryName) {
        for (Library library : libraryTableModel.getLibraries()) {
            String libraryName = library.getName();
            if (libraryName != null && libraryName.startsWith(suggestedLibraryName)) {
                return library;
            }
        }
        return null;
    }

    @Nullable
    private static Library findLibraryByName(Module module, String libraryName) {
        LibraryOrderEntry entry = OrderEntryUtil.findLibraryOrderEntry(ModuleRootManager.getInstance(module), libraryName);
        return entry != null ? entry.getLibrary() : null;
    }

    private static class ChangesToApply {
        private final List<String> clsUrlsToAdd = new ArrayList<String>();

        private final List<String> srcUrlsToAdd = new ArrayList<String>();

        public List<String> getClsUrlsToAdd() {
            return clsUrlsToAdd;
        }

        public List<String> getSrcUrlsToAdd() {
            return srcUrlsToAdd;
        }
    }
}
