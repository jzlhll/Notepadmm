package com.allan.baseparty.content;

/**
 * Interface used for modifying values in a {@link SharedPref}
 * object.  All changes you make in an editor are batched, and not copied
 * back to the original {@link SharedPref} until you call {@link #commit}
 * or {@link #apply}
 */
public interface SharedPrefEditor {
    /**
     * Set a String value in the preferences editor, to be written back once
     * {@link #commit} or {@link #apply} are called.
     *
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.  Passing {@code null}
     *    for this argument is equivalent to calling {@link #remove(String)} with
     *    this key.
     *
     * @return Returns a reference to the same Editor object, so you can
     * chain put calls together.
     */
    SharedPrefEditor putString(String key, String value);

    /**
     * Set an int value in the preferences editor, to be written back once
     * {@link #commit} or {@link #apply} are called.
     *
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     *
     * @return Returns a reference to the same Editor object, so you can
     * chain put calls together.
     */
    SharedPrefEditor putInt(String key, int value);

    /**
     * Set a long value in the preferences editor, to be written back once
     * {@link #commit} or {@link #apply} are called.
     *
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     *
     * @return Returns a reference to the same Editor object, so you can
     * chain put calls together.
     */
    SharedPrefEditor putLong(String key, long value);

    /**
     * Set a float value in the preferences editor, to be written back once
     * {@link #commit} or {@link #apply} are called.
     *
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     *
     * @return Returns a reference to the same Editor object, so you can
     * chain put calls together.
     */
    SharedPrefEditor putFloat(String key, float value);

    /**
     * Set a boolean value in the preferences editor, to be written back
     * once {@link #commit} or {@link #apply} are called.
     *
     * @param key The name of the preference to modify.
     * @param value The new value for the preference.
     *
     * @return Returns a reference to the same Editor object, so you can
     * chain put calls together.
     */
    SharedPrefEditor putBoolean(String key, boolean value);

    /**
     * Mark in the editor that a preference value should be removed, which
     * will be done in the actual preferences once {@link #commit} is
     * called.
     *
     * <p>Note that when committing back to the preferences, all removals
     * are done first, regardless of whether you called remove before
     * or after put methods on this editor.
     *
     * @param key The name of the preference to remove.
     *
     * @return Returns a reference to the same Editor object, so you can
     * chain put calls together.
     */
    SharedPrefEditor remove(String key);

    /**
     * Mark in the editor to remove <em>all</em> values from the
     * preferences.  Once commit is called, the only remaining preferences
     * will be any that you have defined in this editor.
     *
     * <p>Note that when committing back to the preferences, the clear
     * is done first, regardless of whether you called clear before
     * or after put methods on this editor.
     *
     * @return Returns a reference to the same Editor object, so you can
     * chain put calls together.
     */
    SharedPrefEditor clear();

    /**
     * Commit your preferences changes back from this Editor to the
     * {@link SharedPref} object it is editing.  This atomically
     * performs the requested modifications, replacing whatever is currently
     * in the com.base.content.SharedPref.
     *
     * <p>Note that when two editors are modifying preferences at the same
     * time, the last one to call commit wins.
     *
     * <p>If you don't care about the return value and you're
     * using this from your application's main thread, consider
     * using {@link #apply} instead.
     *
     * @return Returns true if the new values were successfully written
     * to persistent storage.
     */
    boolean commit();

    /**
     * Commit your preferences changes back from this Editor to the
     * {@link SharedPref} object it is editing.  This atomically
     * performs the requested modifications, replacing whatever is currently
     * in the com.base.content.SharedPref.
     *
     * <p>Note that when two editors are modifying preferences at the same
     * time, the last one to call apply wins.
     *
     * <p>Unlike {@link #commit}, which writes its preferences out
     * to persistent storage synchronously, {@link #apply}
     * commits its changes to the in-memory
     * {@link SharedPref} immediately but starts an
     * asynchronous commit to disk and you won't be notified of
     * any failures.  If another editor on this
     * {@link SharedPref} does a regular {@link #commit}
     * while a {@link #apply} is still outstanding, the
     * {@link #commit} will block until all async commits are
     * completed as well as the commit itself.
     *
     * <p>As {@link SharedPref} instances are singletons within
     * a process, it's safe to replace any instance of {@link #commit} with
     * {@link #apply} if you were already ignoring the return value.
     *
     * <p>You don't need to worry about Android component
     * lifecycles and their interaction with <code>apply()</code>
     * writing to disk.  The framework makes sure in-flight disk
     * writes from <code>apply()</code> complete before switching
     * states.
     *
     * <p class='note'>The com.base.content.SharedPref.Editor interface
     * isn't expected to be implemented directly.  However, if you
     * previously did implement it and are now getting errors
     * about missing <code>apply()</code>, you can simply call
     * {@link #commit} from <code>apply()</code>.
     */
    void apply();
}
