#!/usr/bin/env python3
"""
Converts poems_bundle.json to SQLite database for Android Room integration.
This creates a pre-populated database that can be bundled with the app.
"""

import json
import sqlite3
import os
import sys
from pathlib import Path


def create_poems_database(json_file_path, db_output_path):
    """
    Convert poems JSON to SQLite database with Room-compatible schema.

    Args:
        json_file_path: Path to poems_bundle.json
        db_output_path: Path where SQLite database should be created
    """
    print(f"📚 Converting poems from {json_file_path} to SQLite database...")

    # Read and parse JSON file
    try:
        with open(json_file_path, 'r', encoding='utf-8') as file:
            data = json.load(file)
        file_size = os.path.getsize(json_file_path) / (1024 * 1024)
        print(f"✅ Successfully loaded JSON file ({file_size:.1f} MB)")
    except Exception as e:
        print(f"❌ Error reading JSON file: {e}")
        return False

    # Create SQLite database
    try:
        # Remove existing database if it exists
        if os.path.exists(db_output_path):
            os.remove(db_output_path)

        conn = sqlite3.connect(db_output_path)
        cursor = conn.cursor()

        # Create poems table with Room-compatible schema
        cursor.execute('''
            CREATE TABLE poems (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                author TEXT NOT NULL,
                content TEXT NOT NULL,
                firstLine TEXT NOT NULL,
                sourceType TEXT NOT NULL DEFAULT 'BUNDLED'
            )
        ''')

        # Create indexes for fast searching
        cursor.execute('CREATE INDEX index_poems_title ON poems(title)')
        cursor.execute('CREATE INDEX index_poems_author ON poems(author)')
        cursor.execute('CREATE INDEX index_poems_content ON poems(content)')
        cursor.execute('CREATE INDEX index_poems_firstLine ON poems(firstLine)')
        cursor.execute('''
            CREATE INDEX index_poems_title_author 
            ON poems(title, author)
        ''')

        print("✅ Created database schema with search indexes")

        # Extract and insert poems
        total_poems = 0
        collections_processed = 0

        if 'collections' in data:
            for collection in data['collections']:
                collection_name = collection.get('name', 'Unknown Collection')
                poems = collection.get('poems', [])
                print(f"📖 Processing collection: '{collection_name}' "
                      f"({len(poems)} poems)")

                for poem in poems:
                    poem_data = (
                        poem.get('id', ''),
                        poem.get('title', ''),
                        poem.get('author', ''),
                        poem.get('text', ''),  # Maps to 'content' field
                        poem.get('first_line', ''),  # Maps to 'firstLine' field
                        'BUNDLED'  # sourceType
                    )
                    cursor.execute('''
                        INSERT INTO poems 
                        (id, title, author, content, firstLine, sourceType)
                        VALUES (?, ?, ?, ?, ?, ?)
                    ''', poem_data)
                    total_poems += 1

                    # Progress indicator for large datasets
                    if total_poems % 1000 == 0:
                        print(f" 📝 Processed {total_poems} poems...")

                collections_processed += 1

        # Commit changes
        conn.commit()

        # Get final database statistics
        cursor.execute('SELECT COUNT(*) FROM poems')
        final_count = cursor.fetchone()[0]
        
        cursor.execute('SELECT AVG(LENGTH(content)) FROM poems')
        avg_content_length = cursor.fetchone()[0]
        
        cursor.execute('SELECT COUNT(DISTINCT author) FROM poems')
        unique_authors = cursor.fetchone()[0]
        
        conn.close()

        # Database file size
        db_size_mb = os.path.getsize(db_output_path) / (1024 * 1024)
        json_size_mb = os.path.getsize(json_file_path) / (1024 * 1024)

        print(f"\n🎉 Conversion completed successfully!")
        print(f"📊 Database Statistics:")
        print(f"   - Total poems: {final_count:,}")
        print(f"   - Collections processed: {collections_processed}")
        print(f"   - Unique authors: {unique_authors}")
        print(f"   - Average content length: {avg_content_length:.0f} characters")
        print(f"   - Database size: {db_size_mb:.1f} MB")
        print(f"   - Size reduction: {json_size_mb - db_size_mb:.1f} MB smaller")
        print(f"📁 Output database: {db_output_path}")

        return True

    except Exception as e:
        print(f"❌ Error creating database: {e}")
        if 'conn' in locals():
            conn.close()
        return False


def main():
    """Main function to handle command line execution."""
    script_dir = Path(__file__).parent
    project_root = script_dir.parent

    # Default paths
    json_file = project_root / "app" / "src" / "main" / "assets" / "poems_bundle.json"
    db_output_dir = project_root / "app" / "src" / "main" / "assets" / "databases"
    db_output_file = db_output_dir / "poetica_poems.db"

    # Create databases directory if it doesn't exist
    db_output_dir.mkdir(parents=True, exist_ok=True)

    print("🔄 Poetica Poems Database Converter")
    print("=" * 50)
    print(f"📂 Input JSON: {json_file}")
    print(f"📂 Output DB: {db_output_file}")
    print()

    # Verify input file exists
    if not json_file.exists():
        print(f"❌ Error: JSON file not found at {json_file}")
        print("Please ensure poems_bundle.json exists in app/src/main/assets/")
        return 1

    # Convert JSON to SQLite
    success = create_poems_database(str(json_file), str(db_output_file))

    if success:
        print(f"\n✅ Conversion complete! Database ready for Android integration.")
        print(f"💡 Next steps:")
        print(f"   1. Update PoeticaDatabase.kt to use pre-populated database")
        print(f"   2. Modify PoemRepository.kt to handle existing data")
        print(f"   3. Test app startup performance")
        return 0
    else:
        print(f"\n❌ Conversion failed. Please check error messages above.")
        return 1


if __name__ == "__main__":
    sys.exit(main())