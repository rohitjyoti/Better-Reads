package io.javabrains.betterreadsdataloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import connection.DataStaxAstraProperties;
import io.javabrains.betterreadsdataloader.author.Author;
import io.javabrains.betterreadsdataloader.author.AuthorRepository;
import io.javabrains.betterreadsdataloader.book.Book;
import io.javabrains.betterreadsdataloader.book.BookRepository;

@SpringBootApplication
@EnableConfigurationProperties(DataStaxAstraProperties.class)
public class BetterreadsDataLoaderApplication {

	@Autowired
	AuthorRepository authorRepository;

	@Autowired
	BookRepository bookRepository;

	@Value("${datadumb.location.author}")
	private String authorDumbLocation;

	@Value("${datadumb.location.works}")
	private String worksDumbLocation;

	public static void main(String[] args) {
		SpringApplication.run(BetterreadsDataLoaderApplication.class, args);
	}

	private void initAuthors() {
		Path path = Paths.get(authorDumbLocation);
		try (Stream<String> lines = Files.lines(path)) {
			lines.forEach(line -> {
				// Read and parse the line
				String jsonString = line.substring(line.indexOf("{"));
				
				try {
					JSONObject jsonObject = new JSONObject(jsonString);
					// Construct the Author object
					Author author = new Author();
					author.setName(jsonObject.optString("name"));
					author.setId(jsonObject.optString("key").replace("/authors/", ""));
					author.setPersonalName(jsonObject.optString("personal_name"));

					System.out.println("Saving author " + author.getName() + "...");
					// Persist using repository
					authorRepository.save(author);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void initWorks() {
		Path path = Paths.get(worksDumbLocation);
		try (Stream<String> lines = Files.lines(path)) {
			lines.forEach(line -> {
				// Read and parse the line
				String jsonString = line.substring(line.indexOf("{"));
				DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
				try {
					
					JSONObject jsonObject = new JSONObject(jsonString);
					// Construct the Author object
					Book book = new Book();
					
					book.setId(jsonObject.getString("key").replace("/works/", ""));
					
					book.setName(jsonObject.optString("title"));
					
					JSONObject descriptionObj = jsonObject.optJSONObject("description");
					if (descriptionObj != null){
						book.setBook_description(descriptionObj.optString("value"));
					}
					
					JSONObject publishedObj = jsonObject.optJSONObject("created");
					if(publishedObj != null){
						String dataStr = publishedObj.getString("value");
						book.setPublishedDate(LocalDate.parse(dataStr, dateFormat));
					}
					
					JSONArray coverJSONArr = jsonObject.optJSONArray("covers");
					if (coverJSONArr != null) {
						List<String> coverIds = new ArrayList<>();
						for(int i=0;i< coverJSONArr.length();i++){
							// coverIds.add(coverJSONArr.getString(i));
							coverIds.add(String.valueOf(coverJSONArr.getInt(i)));
						}
						book.setCoverIds(coverIds);
					}

					JSONArray authorJSONArr = jsonObject.optJSONArray("authors");
					if (authorJSONArr != null) {
						List<String> authorIds = new ArrayList<>();
						for(int i=0;i< authorJSONArr.length();i++){
							String authorId = authorJSONArr.getJSONObject(i).getJSONObject("author").getString("key").replace("/author/", "");
							authorIds.add(authorId);
						}
						book.setAuthorIds(authorIds);
						List<String> authorNames = authorIds.stream().map(id -> authorRepository.findById(id)).map(optionalAuthor -> {
								if(!optionalAuthor.isPresent()) return "Unknown Author";
								return optionalAuthor.get().getName();
							}).collect(Collectors.toList());

							book.setAuthorNames(authorNames);
						}
						
						System.out.println("Saving book " + book.getName() + "...");
						// Persist using repository
						bookRepository.save(book);
					} catch (JSONException e) {
						e.printStackTrace();
					}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@PostConstruct
	public void start() {
		initAuthors();
		initWorks();

	}

	@Bean
	public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
		Path bundle = astraProperties.getSecureConnectBundle().toPath();
		return builder -> builder.withCloudSecureConnectBundle(bundle);
	}
}
