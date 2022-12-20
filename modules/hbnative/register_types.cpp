/* register_types.cpp */

#include "register_types.h"

#include "core/class_db.h"
#include "ph_audio_stream_editor.h"
#include "ph_audio_stream_preview.h"
#include "ph_singleton.h"
#include "multi_spin_box.h"

#include "process/process.h"
#include "process/process_tiny_process_lib.h"
#ifdef UNIX_ENABLED
#include "process/process_unix.h"
#endif
#ifdef WINDOWS_ENABLED
#include "process/process_windows.h"
#endif

static PHAudioStreamPreviewGenerator *preview_generator_ptr = NULL;
static PHNative *ph_ptr = NULL;

void register_hbnative_types() {
	preview_generator_ptr = memnew(PHAudioStreamPreviewGenerator);
	ph_ptr = memnew(PHNative);

#ifdef UNIX_ENABLED
	ProcessUnix::make_default();
#endif

#ifdef WINDOWS_ENABLED
	ProcessWindows::make_default();
#endif

	ClassDB::register_class<PHAudioStreamPreviewGenerator>();
	ClassDB::register_class<PHAudioStreamEditor>();
	ClassDB::register_class<PHAudioStreamPreview>();
	ClassDB::register_virtual_class<Process>();
	ClassDB::register_virtual_class<PHNative>();
	Engine::get_singleton()->add_singleton(Engine::Singleton("PHAudioStreamPreviewGenerator", PHAudioStreamPreviewGenerator::get_singleton()));
	Engine::get_singleton()->add_singleton(Engine::Singleton("PHNative", PHNative::get_singleton()));
	ClassDB::register_class<MultiSpinBox>();
}

void unregister_hbnative_types() {
	memdelete(preview_generator_ptr);
	memdelete(ph_ptr);
}
