import { useState, useEffect, useRef, type FormEvent, type DragEvent } from 'react';
import {
  Settings,
  X,
  Download,
  Upload,
  FileText,
  ShieldCheck,
  UserPlus,
  Trash2,
  Loader2,
  CheckCircle2,
  AlertCircle,
  Search,
  HardDriveDownload,
  FolderSync,
} from 'lucide-react';
import { exportOpml, importOpml } from '../../api/opml';
import { useWhitelist } from '../../hooks/useWhitelist';
import { useAuth } from '../../hooks/useAuth';
import { cn } from '../../utils/cn';

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onFeedsChanged: () => Promise<void>;
}

type TabType = 'opml' | 'whitelist';

export function SettingsModal({ isOpen, onClose, onFeedsChanged }: SettingsModalProps) {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<TabType>('opml');

  // OPML state
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [importing, setImporting] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [opmlSuccess, setOpmlSuccess] = useState<string | null>(null);
  const [opmlError, setOpmlError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Whitelist state
  const isAdmin = Boolean(user?.isAdmin);
  const {
    entries: whitelistEntries,
    loading: whitelistLoading,
    error: whitelistError,
    refreshEntries,
    addEntry: addWhitelistEntry,
    removeEntry: removeWhitelistEntry,
    setError: setWhitelistError,
  } = useWhitelist(isOpen && activeTab === 'whitelist' && isAdmin);

  const [newEmail, setNewEmail] = useState('');
  const [newNote, setNewNote] = useState('');
  const [addingEmail, setAddingEmail] = useState(false);
  const [whitelistSuccess, setWhitelistSuccess] = useState<string | null>(null);
  const [whitelistFilter, setWhitelistFilter] = useState('');

  // Handle Escape key to close modal
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape' && isOpen && !importing && !exporting && !addingEmail) {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, importing, exporting, addingEmail, onClose]);

  // Reset form status when modal opens or closes
  useEffect(() => {
    if (isOpen) {
      setOpmlSuccess(null);
      setOpmlError(null);
      setWhitelistSuccess(null);
      setSelectedFile(null);
      if (isAdmin && activeTab === 'whitelist') {
        refreshEntries();
      }
    }
  }, [isOpen, activeTab, isAdmin, refreshEntries]);

  if (!isOpen) return null;

  // Handle OPML Export
  const handleExport = async () => {
    try {
      setExporting(true);
      setOpmlError(null);
      setOpmlSuccess(null);
      await exportOpml();
      setOpmlSuccess('Subscriptions exported successfully as marginalia-subscriptions.opml');
    } catch (err) {
      setOpmlError(err instanceof Error ? err.message : 'Failed to export OPML');
    } finally {
      setExporting(false);
    }
  };

  // Handle OPML File Selection & Drag-and-Drop
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
      setOpmlError(null);
      setOpmlSuccess(null);
    }
  };

  const handleDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const file = e.dataTransfer.files[0];
      if (file.name.endsWith('.opml') || file.name.endsWith('.xml') || file.type.includes('xml')) {
        setSelectedFile(file);
        setOpmlError(null);
        setOpmlSuccess(null);
      } else {
        setOpmlError('Please drop a valid .opml or .xml file.');
      }
    }
  };

  // Handle OPML Import Submission
  const handleImport = async (e: FormEvent) => {
    e.preventDefault();
    if (!selectedFile) return;

    try {
      setImporting(true);
      setOpmlError(null);
      setOpmlSuccess(null);

      const importedFeeds = await importOpml(selectedFile);
      setOpmlSuccess(`Successfully imported ${importedFeeds.length} feeds into your library.`);
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }

      // Refresh sidebar and category state immediately
      await onFeedsChanged();
    } catch (err) {
      setOpmlError(err instanceof Error ? err.message : 'Failed to import OPML file');
    } finally {
      setImporting(false);
    }
  };

  // Handle Whitelist Add Entry
  const handleAddWhitelist = async (e: FormEvent) => {
    e.preventDefault();
    if (!newEmail.trim()) return;

    try {
      setAddingEmail(true);
      setWhitelistError(null);
      setWhitelistSuccess(null);

      await addWhitelistEntry(newEmail.trim(), newNote.trim() || undefined);
      setWhitelistSuccess(`Added ${newEmail.trim().toLowerCase()} to the whitelist.`);
      setNewEmail('');
      setNewNote('');
    } catch (err) {
      // Error message is already formatted and set in useWhitelist hook
      console.error('Add whitelist error:', err);
    } finally {
      setAddingEmail(false);
    }
  };

  // Handle Whitelist Delete Entry
  const handleDeleteWhitelist = async (id: number, email: string) => {
    if (!confirm(`Are you sure you want to remove "${email}" from the authorized whitelist?`)) {
      return;
    }

    try {
      setWhitelistError(null);
      setWhitelistSuccess(null);
      await removeWhitelistEntry(id);
      setWhitelistSuccess(`Removed ${email} from the whitelist.`);
    } catch (err) {
      console.error('Delete whitelist error:', err);
    }
  };

  // Filter whitelist entries
  const filteredWhitelist = whitelistEntries.filter((entry) => {
    const query = whitelistFilter.toLowerCase().trim();
    if (!query) return true;
    return (
      entry.email.toLowerCase().includes(query) ||
      (entry.note && entry.note.toLowerCase().includes(query)) ||
      (entry.addedBy && entry.addedBy.toLowerCase().includes(query))
    );
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink-900/40 backdrop-blur-xs animate-in fade-in duration-200">
      <div
        className="w-full max-w-2xl bg-paper-50 border border-paper-300 rounded-2xl shadow-xl overflow-hidden animate-in zoom-in-95 duration-200 flex flex-col max-h-[90vh]"
        role="dialog"
        aria-modal="true"
      >
        {/* Modal Header */}
        <div className="px-6 py-4 border-b border-paper-200 flex items-center justify-between shrink-0 bg-paper-50">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 rounded-lg bg-amberAccent-100 text-amberAccent-800">
              <Settings className="w-4 h-4" />
            </div>
            <div>
              <h3 className="font-serif text-lg font-bold text-ink-900 leading-tight">
                Settings & Management
              </h3>
              <p className="text-xs text-ink-500">
                Manage subscriptions, OPML data exchange, and access whitelist
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={importing || exporting || addingEmail}
            className="p-1.5 text-ink-400 hover:text-ink-700 hover:bg-paper-200 rounded-lg transition-colors"
            title="Close"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Tab Switcher */}
        <div className="flex border-b border-paper-200 px-6 bg-paper-100/60 shrink-0">
          <button
            type="button"
            onClick={() => setActiveTab('opml')}
            className={cn(
              'flex items-center space-x-2 py-3 px-3 text-xs font-semibold border-b-2 transition-colors -mb-px',
              activeTab === 'opml'
                ? 'border-amberAccent-700 text-amberAccent-900'
                : 'border-transparent text-ink-500 hover:text-ink-800'
            )}
          >
            <FolderSync className="w-3.5 h-3.5" />
            <span>OPML Subscriptions</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('whitelist')}
            className={cn(
              'flex items-center space-x-2 py-3 px-3 text-xs font-semibold border-b-2 transition-colors -mb-px',
              activeTab === 'whitelist'
                ? 'border-amberAccent-700 text-amberAccent-900'
                : 'border-transparent text-ink-500 hover:text-ink-800'
            )}
          >
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Access Whitelist</span>
            <span
              className={cn(
                'text-[10px] uppercase font-mono px-1.5 py-0.2 rounded-full font-bold',
                isAdmin ? 'bg-amberAccent-200 text-amberAccent-900' : 'bg-paper-300 text-ink-500'
              )}
            >
              Admin
            </span>
          </button>
        </div>

        {/* Modal Scrollable Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1">
          {/* TAB 1: OPML Subscriptions */}
          {activeTab === 'opml' && (
            <div className="space-y-6">
              {/* Feedback banners */}
              {opmlSuccess && (
                <div className="p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl flex items-start space-x-2.5 text-xs text-emerald-800 animate-in fade-in">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                  <div className="leading-relaxed">{opmlSuccess}</div>
                </div>
              )}

              {opmlError && (
                <div className="p-3.5 bg-red-50 border border-red-200 rounded-xl flex items-start space-x-2.5 text-xs text-red-800 animate-in fade-in">
                  <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                  <div className="leading-relaxed">{opmlError}</div>
                </div>
              )}

              {/* Section 1: Export OPML */}
              <div className="p-4 rounded-xl border border-paper-300 bg-paper-100/40 space-y-3">
                <div className="flex items-start justify-between">
                  <div>
                    <h4 className="font-serif text-sm font-bold text-ink-900 flex items-center space-x-2">
                      <HardDriveDownload className="w-4 h-4 text-amberAccent-700" />
                      <span>Export Subscriptions</span>
                    </h4>
                    <p className="text-xs text-ink-600 mt-1 leading-relaxed">
                      Download all your subscribed feeds and folder categorizations as a standard OPML 2.0 XML
                      file to backup or transfer to any standard feed reader.
                    </p>
                  </div>
                </div>

                <div className="pt-2">
                  <button
                    type="button"
                    onClick={handleExport}
                    disabled={exporting}
                    className="flex items-center space-x-2 px-4 py-2 bg-paper-50 hover:bg-paper-200 border border-paper-300 text-ink-800 rounded-xl text-xs font-semibold transition-colors shadow-2xs disabled:opacity-50"
                  >
                    {exporting ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin text-amberAccent-700" />
                        <span>Generating OPML...</span>
                      </>
                    ) : (
                      <>
                        <Download className="w-3.5 h-3.5 text-amberAccent-700" />
                        <span>Export Subscriptions (.opml)</span>
                      </>
                    )}
                  </button>
                </div>
              </div>

              {/* Section 2: Import OPML */}
              <form onSubmit={handleImport} className="p-4 rounded-xl border border-paper-300 bg-paper-100/40 space-y-4">
                <div>
                  <h4 className="font-serif text-sm font-bold text-ink-900 flex items-center space-x-2">
                    <Upload className="w-4 h-4 text-amberAccent-700" />
                    <span>Import Subscriptions</span>
                  </h4>
                  <p className="text-xs text-ink-600 mt-1 leading-relaxed">
                    Upload an OPML or XML file exported from another reader. Folders and feeds will be merged into your
                    reading library without duplicate subscriptions.
                  </p>
                </div>

                {/* Dropzone */}
                <div
                  onDragOver={handleDragOver}
                  onDragLeave={handleDragLeave}
                  onDrop={handleDrop}
                  className={cn(
                    'border-2 border-dashed rounded-xl p-5 text-center transition-colors cursor-pointer',
                    isDragging
                      ? 'border-amberAccent-600 bg-amberAccent-50/60'
                      : 'border-paper-300 bg-paper-50 hover:bg-paper-200/40'
                  )}
                  onClick={() => fileInputRef.current?.click()}
                >
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".opml,.xml,text/xml,application/xml"
                    onChange={handleFileChange}
                    className="hidden"
                  />

                  {selectedFile ? (
                    <div className="flex items-center justify-center space-x-2 text-xs text-ink-900 font-medium">
                      <FileText className="w-4 h-4 text-amberAccent-700" />
                      <span className="font-mono text-xs">{selectedFile.name}</span>
                      <span className="text-ink-400 text-[11px]">
                        ({(selectedFile.size / 1024).toFixed(1)} KB)
                      </span>
                    </div>
                  ) : (
                    <div className="space-y-1.5">
                      <div className="flex justify-center text-ink-400">
                        <FolderSync className="w-6 h-6 text-amberAccent-700" />
                      </div>
                      <p className="text-xs text-ink-800 font-medium">
                        Drag and drop your <span className="font-mono font-bold text-amberAccent-800">.opml</span> or{' '}
                        <span className="font-mono font-bold text-amberAccent-800">.xml</span> file here
                      </p>
                      <p className="text-[11px] text-ink-500">or click to browse from your computer</p>
                    </div>
                  )}
                </div>

                {/* Import Submit Button */}
                <div className="flex items-center justify-between pt-1">
                  {selectedFile ? (
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedFile(null);
                        if (fileInputRef.current) fileInputRef.current.value = '';
                      }}
                      className="text-xs text-ink-500 hover:text-ink-800"
                    >
                      Clear Selection
                    </button>
                  ) : (
                    <span className="text-[11px] text-ink-400">Standard OPML 1.0 and 2.0 supported</span>
                  )}

                  <button
                    type="submit"
                    disabled={!selectedFile || importing}
                    className="flex items-center space-x-2 px-5 py-2 bg-amberAccent-700 hover:bg-amberAccent-800 disabled:opacity-50 text-paper-50 rounded-xl text-xs font-semibold transition-colors shadow-xs"
                  >
                    {importing ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Importing Feeds...</span>
                      </>
                    ) : (
                      <>
                        <Upload className="w-3.5 h-3.5" />
                        <span>Import Feeds</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* TAB 2: Whitelist Management */}
          {activeTab === 'whitelist' && (
            <div className="space-y-5">
              {!isAdmin ? (
                /* Non-admin access warning */
                <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 space-y-1.5">
                  <div className="flex items-center space-x-2 font-bold text-amber-950">
                    <AlertCircle className="w-4 h-4 text-amber-700" />
                    <span>Administrative Privileges Required</span>
                  </div>
                  <p className="leading-relaxed text-amber-800">
                    Managing the instance email whitelist is restricted to configured administrators. Your account (
                    <span className="font-mono font-semibold">{user?.email}</span>) does not currently possess admin rights.
                  </p>
                </div>
              ) : (
                /* Admin Whitelist Controls */
                <>
                  {/* Feedback banners */}
                  {whitelistSuccess && (
                    <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl flex items-start space-x-2 text-xs text-emerald-800 animate-in fade-in">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                      <div>{whitelistSuccess}</div>
                    </div>
                  )}

                  {whitelistError && (
                    <div className="p-3 bg-red-50 border border-red-200 rounded-xl flex items-start space-x-2 text-xs text-red-800 animate-in fade-in">
                      <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                      <div>{whitelistError}</div>
                    </div>
                  )}

                  {/* Add Whitelist Form */}
                  <form onSubmit={handleAddWhitelist} className="p-4 rounded-xl border border-paper-300 bg-paper-100/40 space-y-3">
                    <h4 className="font-serif text-sm font-bold text-ink-900 flex items-center space-x-2">
                      <UserPlus className="w-4 h-4 text-amberAccent-700" />
                      <span>Authorize New Email</span>
                    </h4>
                    <p className="text-xs text-ink-600">
                      Users with this email will be allowed to log in via OAuth2 (Google / GitHub) or developer login.
                    </p>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-1">
                      <div>
                        <label htmlFor="whitelist-email" className="block text-[11px] font-semibold text-ink-700 mb-1">
                          Email Address <span className="text-amberAccent-700">*</span>
                        </label>
                        <input
                          id="whitelist-email"
                          type="email"
                          value={newEmail}
                          onChange={(e) => setNewEmail(e.target.value)}
                          placeholder="colleague@example.com"
                          required
                          className="w-full px-3 py-1.5 bg-paper-50 border border-paper-300 rounded-lg text-xs font-mono text-ink-900 focus:outline-none focus:ring-2 focus:ring-amberAccent-700/20 focus:border-amberAccent-700"
                        />
                      </div>
                      <div>
                        <label htmlFor="whitelist-note" className="block text-[11px] font-semibold text-ink-700 mb-1">
                          Note / Identifier <span className="text-ink-400 font-normal">(Optional)</span>
                        </label>
                        <input
                          id="whitelist-note"
                          type="text"
                          value={newNote}
                          onChange={(e) => setNewNote(e.target.value)}
                          placeholder="e.g. Work laptop, Partner"
                          className="w-full px-3 py-1.5 bg-paper-50 border border-paper-300 rounded-lg text-xs text-ink-900 focus:outline-none focus:ring-2 focus:ring-amberAccent-700/20 focus:border-amberAccent-700"
                        />
                      </div>
                    </div>

                    <div className="flex justify-end pt-1">
                      <button
                        type="submit"
                        disabled={addingEmail || !newEmail.trim()}
                        className="flex items-center space-x-2 px-4 py-1.5 bg-amberAccent-700 hover:bg-amberAccent-800 disabled:opacity-50 text-paper-50 rounded-lg text-xs font-semibold transition-colors shadow-2xs"
                      >
                        {addingEmail ? (
                          <>
                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            <span>Authorizing...</span>
                          </>
                        ) : (
                          <>
                            <UserPlus className="w-3.5 h-3.5" />
                            <span>Add to Whitelist</span>
                          </>
                        )}
                      </button>
                    </div>
                  </form>

                  {/* Whitelist Entries List */}
                  <div className="space-y-2.5">
                    <div className="flex items-center justify-between">
                      <div className="text-xs font-semibold text-ink-800 font-serif">
                        Authorized Accounts ({whitelistEntries.length})
                      </div>

                      {/* Quick Search */}
                      {whitelistEntries.length > 2 && (
                        <div className="relative w-48">
                          <Search className="w-3 h-3 absolute left-2.5 top-2.5 text-ink-400" />
                          <input
                            type="text"
                            value={whitelistFilter}
                            onChange={(e) => setWhitelistFilter(e.target.value)}
                            placeholder="Filter accounts..."
                            className="w-full pl-7 pr-3 py-1 bg-paper-50 border border-paper-300 rounded-lg text-[11px] text-ink-800 focus:outline-none focus:border-amberAccent-700"
                          />
                        </div>
                      )}
                    </div>

                    {whitelistLoading ? (
                      <div className="p-8 text-center text-xs text-ink-500 flex items-center justify-center space-x-2">
                        <Loader2 className="w-4 h-4 animate-spin text-amberAccent-700" />
                        <span>Loading whitelist entries...</span>
                      </div>
                    ) : filteredWhitelist.length === 0 ? (
                      <div className="p-6 text-center text-xs text-ink-400 border border-paper-200 rounded-xl bg-paper-100/30">
                        {whitelistFilter ? 'No accounts match your filter.' : 'No whitelisted accounts found.'}
                      </div>
                    ) : (
                      <div className="border border-paper-300 rounded-xl overflow-hidden bg-paper-50">
                        <div className="overflow-x-auto">
                          <table className="w-full text-left text-xs">
                            <thead className="bg-paper-100/80 border-b border-paper-200 text-ink-500 font-mono text-[10px] uppercase">
                              <tr>
                                <th className="px-3.5 py-2 font-semibold">Account Email</th>
                                <th className="px-3 py-2 font-semibold">Note</th>
                                <th className="px-3 py-2 font-semibold hidden sm:table-cell">Added By</th>
                                <th className="px-3 py-2 font-semibold hidden md:table-cell">Date</th>
                                <th className="px-3 py-2 font-semibold text-right">Actions</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-paper-200/80">
                              {filteredWhitelist.map((entry) => {
                                const isSelf = user?.email.toLowerCase() === entry.email.toLowerCase();
                                const isSystem = entry.addedBy === 'SYSTEM';

                                return (
                                  <tr key={entry.id} className="hover:bg-paper-100/50 transition-colors">
                                    <td className="px-3.5 py-2.5">
                                      <div className="flex items-center space-x-2">
                                        <span className="font-mono text-ink-900 font-medium truncate max-w-[200px]">
                                          {entry.email}
                                        </span>
                                        {isSelf && (
                                          <span className="px-1.5 py-0.2 rounded-full text-[9px] font-bold bg-amberAccent-100 text-amberAccent-900">
                                            You
                                          </span>
                                        )}
                                        {isSystem && (
                                          <span className="px-1.5 py-0.2 rounded-full text-[9px] font-bold bg-purple-100 text-purple-800">
                                            Bootstrap
                                          </span>
                                        )}
                                      </div>
                                    </td>
                                    <td className="px-3 py-2.5 text-ink-600 truncate max-w-[120px]">
                                      {entry.note || <span className="text-ink-400 italic">—</span>}
                                    </td>
                                    <td className="px-3 py-2.5 text-ink-500 text-[11px] hidden sm:table-cell truncate max-w-[120px]">
                                      {entry.addedBy || '—'}
                                    </td>
                                    <td className="px-3 py-2.5 text-ink-400 text-[11px] font-mono hidden md:table-cell whitespace-nowrap">
                                      {entry.createdAt ? new Date(entry.createdAt).toLocaleDateString() : '—'}
                                    </td>
                                    <td className="px-3 py-2.5 text-right whitespace-nowrap">
                                      {isSelf ? (
                                        <button
                                          type="button"
                                          disabled
                                          className="p-1 text-ink-300 cursor-not-allowed"
                                          title="Cannot remove your own logged-in account"
                                        >
                                          <Trash2 className="w-3.5 h-3.5" />
                                        </button>
                                      ) : isSystem ? (
                                        <button
                                          type="button"
                                          disabled
                                          className="p-1 text-ink-300 cursor-not-allowed"
                                          title="Bootstrap account managed via environment variable"
                                        >
                                          <Trash2 className="w-3.5 h-3.5" />
                                        </button>
                                      ) : (
                                        <button
                                          type="button"
                                          onClick={() => handleDeleteWhitelist(entry.id, entry.email)}
                                          className="p-1 text-ink-400 hover:text-red-700 hover:bg-red-50 rounded transition-colors"
                                          title={`Remove ${entry.email}`}
                                        >
                                          <Trash2 className="w-3.5 h-3.5" />
                                        </button>
                                      )}
                                    </td>
                                  </tr>
                                );
                              })}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-3 border-t border-paper-200 bg-paper-100/50 flex justify-end shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-ink-700 hover:text-ink-950 hover:bg-paper-200 rounded-xl transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}
